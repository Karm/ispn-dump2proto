package biz.karms.protostream;

import biz.karms.cache.pojo.WhitelistedRecord;
import biz.karms.protostream.marshallers.ActionMarshaller;
import biz.karms.protostream.marshallers.CoreCacheMarshaller;
import biz.karms.protostream.marshallers.SinkitCacheEntryMarshaller;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static biz.karms.Dump2Proto.GENERATED_PROTOFILES_DIRECTORY;
import static biz.karms.Dump2Proto.D2P_CACHE_PROTOBUF;
import static biz.karms.Dump2Proto.attr;
import static biz.karms.Dump2Proto.options;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Michal Karm Babacek
 */
public class WhitelistProtostreamGenerator implements Runnable {

    private static final Logger log = Logger.getLogger(WhitelistProtostreamGenerator.class.getName());

    private final RemoteCache<String, WhitelistedRecord> whitelistCache;

    private static final String whiteListFilePath = GENERATED_PROTOFILES_DIRECTORY + "/whitelist.bin";
    private static final String whiteListFilePathTmp = GENERATED_PROTOFILES_DIRECTORY + "/whitelist.bin.tmp";
    private static final String whiteListFileMd5 = GENERATED_PROTOFILES_DIRECTORY + "/whitelist.bin.md5";
    private static final String whiteListFileMd5Tmp = GENERATED_PROTOFILES_DIRECTORY + "/whitelist.bin.md5.tmp";

    public WhitelistProtostreamGenerator(final RemoteCache<String, WhitelistedRecord> whitelistCache) {
        this.whitelistCache = whitelistCache;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        // TODO: Well, this hurts...  We wil probably need to use retrieve(...) and operate in chunks.
        // https://github.com/infinispan/infinispan/pull/4975
        final Map<String, Action> whitelist = whitelistCache.withFlags(Flag.SKIP_CACHE_LOAD).keySet().stream().collect(Collectors.toMap(Function.identity(), s -> Action.WHITE));
        log.info("WhitelistProtostreamGenerator: Pulling whitelist data took: " + (System.currentTimeMillis() - start) + " ms");
        start = System.currentTimeMillis();
        final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
        try {
            ctx.registerProtoFiles(FileDescriptorSource.fromResources(D2P_CACHE_PROTOBUF));
        } catch (IOException e) {
            e.printStackTrace();
        }
        ctx.registerMarshaller(new SinkitCacheEntryMarshaller());
        ctx.registerMarshaller(new CoreCacheMarshaller());
        ctx.registerMarshaller(new ActionMarshaller());

        final Path whiteListFilePathTmpP = Paths.get(whiteListFilePathTmp);
        final Path whiteListFilePathP = Paths.get(whiteListFilePath);
        try {
            Files.newByteChannel(whiteListFilePathTmpP, options, attr).write(ProtobufUtil.toByteBuffer(ctx, whitelist));
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("WhitelistProtostreamGenerator: Serialization to " + whiteListFilePathTmp + " took: " + (System.currentTimeMillis() - start) + " ms");
        start = System.currentTimeMillis();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(new File(whiteListFilePathTmp));
            Files.write(Paths.get(whiteListFileMd5Tmp), DigestUtils.md5Hex(fis).getBytes());
            // There is a race condition when we swap files while REST API is reading them...
            Files.move(whiteListFilePathTmpP, whiteListFilePathP, REPLACE_EXISTING);
            Files.move(Paths.get(whiteListFileMd5Tmp), Paths.get(whiteListFileMd5), REPLACE_EXISTING);
        } catch (IOException e) {
            log.severe("WhitelistProtostreamGenerator: failed protofile manipulation");
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    log.severe("WhitelistProtostreamGenerator: Failed to close MD5 file stream.");
                }
            }
        }
        log.info("WhitelistProtostreamGenerator: MD5 sum and move took: " + (System.currentTimeMillis() - start) + " ms");
    }
}
