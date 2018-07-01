package biz.karms.protostream;

import biz.karms.protostream.ioc.marshallers.AccuracyMarshaller;
import biz.karms.protostream.ioc.marshallers.BlacklistedRecordListMarshaller;
import biz.karms.protostream.ioc.marshallers.BlacklistedRecordMarshaller;
import biz.karms.protostream.ioc.marshallers.NameNumberMarshaller;
import biz.karms.protostream.ioc.marshallers.SourceMarshaller;
import biz.karms.protostream.ioc.marshallers.TypeIocIDMarshaller;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import org.apache.commons.codec.digest.DigestUtils;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static biz.karms.Dump2Proto.D2P_CACHE_PROTOBUF;
import static biz.karms.Dump2Proto.GENERATED_PROTOFILES_DIRECTORY;
import static biz.karms.Dump2Proto.attr;
import static biz.karms.Dump2Proto.options;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Michal Karm Babacek
 */
public class IoCDumper implements Runnable {

    private static final Logger log = Logger.getLogger(IoCDumper.class.getName());

    private final RemoteCache<String, BlacklistedRecord> blacklistCache;

    private static final int MAX_BULK_SIZE = 10_000;

    private static final String iocDumpFilePath = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin";
    private static final String iocDumpFilePathTmp = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin.tmp";
    private static final String iocDumpFileMd5 = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin.md5";
    private static final String iocDumpFileMd5Tmp = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin.md5.tmp";
    private static final String BLACKLISTED_RECORD_PROTOBUF = "/sinkitprotobuf/blacklisted_record.proto";

    public IoCDumper(final RemoteCache<String, BlacklistedRecord> blacklistCache) {
        this.blacklistCache = blacklistCache;
    }

    @Override
    public void run() {
        long overallStart = System.currentTimeMillis();
        long start = System.currentTimeMillis();
        // keySet on the cache - a very expensive call, dozens of seconds for single digit millions o records
        final Set<String> iocKeys = new HashSet<>(blacklistCache.withFlags(Flag.SKIP_CACHE_LOAD).keySet());
        final ArrayList<BlacklistedRecord> iocs = new ArrayList<>(MAX_BULK_SIZE);
        final int bulks = (iocKeys.size() % MAX_BULK_SIZE == 0) ? iocKeys.size() / MAX_BULK_SIZE : iocKeys.size() / MAX_BULK_SIZE + 1;
        log.info("IOCDump: There are " + iocKeys.size() + " ioc keys to get data for in " + bulks + " bulks. Ioc keys retrieval took " + (System.currentTimeMillis() - start) + " ms.");

        long startBulks = System.currentTimeMillis();
        for (int iteration = 0; iteration < bulks; iteration++) {
            start = System.currentTimeMillis();
            log.info("IOCDump: Processing bulk " + iteration + " of " + bulks + " bulks...");
            final Set<String> bulkOfKeys = iocKeys.stream().unordered().limit(MAX_BULK_SIZE).collect(Collectors.toSet());
            iocKeys.removeAll(bulkOfKeys);
            // getAll on the cache - a very expensive call
            iocs.addAll(blacklistCache.withFlags(Flag.SKIP_CACHE_LOAD).getAll(bulkOfKeys).values());
            log.info("IOCDump: Retrieved bulk " + iteration + " in " + (System.currentTimeMillis() - start) + " ms.");
        }
        log.info("IOCDump: All " + bulks + " bulks done in " + (System.currentTimeMillis() - startBulks) + " ms.");

        // Serialization fo tiles
        start = System.currentTimeMillis();
        final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
        try {
            ctx.registerProtoFiles(FileDescriptorSource.fromResources(BLACKLISTED_RECORD_PROTOBUF));
        } catch (IOException e) {
            log.log(Level.SEVERE, "Not found " + D2P_CACHE_PROTOBUF + ". Cannot recover, quitting task.", e);
            return;
        }
        ctx.registerMarshaller(new TypeIocIDMarshaller());
        ctx.registerMarshaller(new NameNumberMarshaller());
        ctx.registerMarshaller(new SourceMarshaller());
        ctx.registerMarshaller(new AccuracyMarshaller());
        ctx.registerMarshaller(new BlacklistedRecordMarshaller());
        ctx.registerMarshaller(new BlacklistedRecordListMarshaller());

        Path iocDumpFilePathTmpP = Paths.get(iocDumpFilePathTmp);
        Path iocDumpFilePathP = Paths.get(iocDumpFilePath);
        try (SeekableByteChannel s = Files.newByteChannel(iocDumpFilePathTmpP, options, attr)) {
            s.write(ProtobufUtil.toByteBuffer(ctx, iocs));
            log.info("IOCDump: " + iocDumpFilePathTmp + " written.");
        } catch (IOException e) {
            log.log(Level.SEVERE, "IOCDump: failed protobuffer serialization.", e);
        }
        try (FileInputStream fis = new FileInputStream(new File(iocDumpFilePathTmp))) {
            Files.write(Paths.get(iocDumpFileMd5Tmp), DigestUtils.md5Hex(fis).getBytes());
            log.info("IOCDump: " + iocDumpFileMd5Tmp + " written.");
            // There is a race condition when we swap files while REST API is reading them...
            Files.move(iocDumpFilePathTmpP, iocDumpFilePathP, REPLACE_EXISTING);
            log.info("IOCDump: " + iocDumpFilePathTmp + " moved to " + iocDumpFilePath + ".");
            Files.move(Paths.get(iocDumpFileMd5Tmp), Paths.get(iocDumpFileMd5), REPLACE_EXISTING);
            log.info("IOCDump: " + iocDumpFileMd5Tmp + " moved to " + iocDumpFileMd5 + ".");
        } catch (IOException e) {
            log.log(Level.SEVERE, "IOCDump: failed protofile manipulation.", e);
        }

        log.info("IOCDump: Serialization took: " + (System.currentTimeMillis() - start) + " ms.");
        long overallTimeSpent = (System.currentTimeMillis() - overallStart);
        log.info("IOCDump: All IoC processing took " + overallTimeSpent + " ms.");
    }
}
