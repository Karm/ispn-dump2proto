package biz.karms.protostream;

import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.protostream.ioc.marshallers.*;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import org.apache.commons.codec.digest.DigestUtils;
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
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static biz.karms.Dump2Proto.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Michal Karm Babacek
 */
public class IoCDumper implements Runnable {

    private static final Logger log = Logger.getLogger(IoCDumper.class.getName());

    private final IoCKeeper ioCKeeper;

    private static final String iocDumpFilePath = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin";
    private static final String iocDumpFilePathTmp = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin.tmp";
    private static final String iocDumpFileMd5 = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin.md5";
    private static final String iocDumpFileMd5Tmp = GENERATED_PROTOFILES_DIRECTORY + "/iocdump.bin.md5.tmp";
    public static final String BLACKLISTED_RECORD_PROTOBUF = "/sinkitprotobuf/blacklisted_record.proto";

    public IoCDumper(final IoCKeeper ioCKeeper) {
        this.ioCKeeper = ioCKeeper;
    }

    @Override
    public void run() {
        final List<BlacklistedRecord> iocs = new LinkedList<>(ioCKeeper.getBlacklistedRecords());

        if (iocs.isEmpty()) {
            log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": IoCKeeper holds no IoCs, probably not ready yet. Skipping this iteration.");
            return;
        }

        // Serialization fo tiles
        long start = System.currentTimeMillis();
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
            log.info("Thread " + Thread.currentThread().getName() + ": IOCDump: " + iocDumpFilePathTmp + " written.");
        } catch (IOException e) {
            log.log(Level.SEVERE, "IOCDump: failed protobuffer serialization.", e);
        }
        try (FileInputStream fis = new FileInputStream(new File(iocDumpFilePathTmp))) {
            Files.write(Paths.get(iocDumpFileMd5Tmp), DigestUtils.md5Hex(fis).getBytes());
            log.info("Thread " + Thread.currentThread().getName() + ": IOCDump: " + iocDumpFileMd5Tmp + " written.");
            // There is a race condition when we swap files while REST API is reading them...
            Files.move(iocDumpFilePathTmpP, iocDumpFilePathP, REPLACE_EXISTING);
            log.info("Thread " + Thread.currentThread().getName() + ": IOCDump: " + iocDumpFilePathTmp + " moved to " + iocDumpFilePath + ".");
            Files.move(Paths.get(iocDumpFileMd5Tmp), Paths.get(iocDumpFileMd5), REPLACE_EXISTING);
            log.info("Thread " + Thread.currentThread().getName() + ": IOCDump: " + iocDumpFileMd5Tmp + " moved to " + iocDumpFileMd5 + ".");
        } catch (IOException e) {
            log.log(Level.SEVERE, "IOCDump: failed protofile manipulation.", e);
        }

        log.info("Thread " + Thread.currentThread().getName() + ": IOCDump: Serialization took: " + (System.currentTimeMillis() - start) + " ms.");
    }
}
