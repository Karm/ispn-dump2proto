package biz.karms;

import biz.karms.protostream.ioc.marshallers.*;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.digest.DigestUtils;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static biz.karms.protostream.IoCDumper.BLACKLISTED_RECORD_PROTOBUF;

/**
 * @author Michal Karm Babacek
 */
public class FiddlerTool {
    private static final String D2P_HOTROD_HOST = System.getProperty("D2P_HOTROD_HOST");
    private static final int D2P_HOTROD_PORT = Integer.parseInt(System.getProperty("D2P_HOTROD_PORT", "11322"));
    private static final long D2P_HOTROD_CONN_TIMEOUT_S = (System.getProperties().containsKey("D2P_HOTROD_CONN_TIMEOUT_S")) ?
            Integer.parseInt(System.getProperty("D2P_HOTROD_CONN_TIMEOUT_S")) :
            300;

    private static final int BUCKET_SIZE = 2_000;

    private static final Logger log = Logger.getLogger(FiddlerTool.class.getName());

    private final MyCacheManagerProvider myCacheManagerProvider;

    private final ShutdownHook jvmShutdownHook;

    private static class ShutdownHook extends Thread {
        private final MyCacheManagerProvider myCacheManagerProvider;

        ShutdownHook(final MyCacheManagerProvider myCacheManagerProvider) {
            this.myCacheManagerProvider = myCacheManagerProvider;
        }

        public void run() {
            log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Shutting down.");
            myCacheManagerProvider.destroy();
        }
    }

    private FiddlerTool(final MyCacheManagerProvider myCacheManagerProvider) {
        this.myCacheManagerProvider = myCacheManagerProvider;
        this.jvmShutdownHook = new ShutdownHook(myCacheManagerProvider);
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);
    }

    public void computeAccuracy(final String fqdn) {
        final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder()
                .setLogOutOfSequenceReads(false)
                .build());
        try {
            ctx.registerProtoFiles(FileDescriptorSource.fromResources(BLACKLISTED_RECORD_PROTOBUF));
        } catch (IOException e) {
            log.log(Level.SEVERE, String.format("File %s was not found. Cannot recover, quitting.", BLACKLISTED_RECORD_PROTOBUF));
            return;
        }
        ctx.registerMarshaller(new TypeIocIDMarshaller());
        ctx.registerMarshaller(new NameNumberMarshaller());
        ctx.registerMarshaller(new SourceMarshaller());
        ctx.registerMarshaller(new AccuracyMarshaller());
        ctx.registerMarshaller(new BlacklistedRecordMarshaller());
        ctx.registerMarshaller(new BlacklistedRecordListMarshaller());

        final ArrayList<BlacklistedRecord> records;

        long start = System.currentTimeMillis();

        final RemoteCache<String, BlacklistedRecord> blacklistedCache = myCacheManagerProvider.getBlacklistCache();

        final String fqdnHashed = DigestUtils.md5Hex(fqdn);

        final BlacklistedRecord blacklistedRecord = blacklistedCache.get(fqdnHashed);

        log.log(Level.ALL, new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(blacklistedRecord));

        if (blacklistedRecord.getAccuracy() != null) {
            // Compute maximum from each feed, not overall...
            final Optional<Map.Entry<String, HashMap<String, Integer>>> fdcmp = blacklistedRecord.getAccuracy().entrySet().stream()
                    .max(Comparator.comparingInt(s -> s.getValue().values().stream().mapToInt(Integer::intValue).sum()));
            fdcmp.ifPresent(x -> log.log(Level.ALL, String.format("Old Core: The most accurate feed for domain %s is %s", fqdn, x)));

            log.log(Level.ALL, "The biggest accuracy according to Generator's algorithm is: %d", computeMaxAccuracy(blacklistedRecord));

        } else {
            log.log(Level.ALL, String.format("No accuracy set for tis fqdn %s, terminating.", fqdn));
        }
        log.log(Level.INFO, String.format("Ended in %d ms.", (System.currentTimeMillis() - start)));
    }

    private Integer computeMaxAccuracy(BlacklistedRecord record) {
        final HashMap<String, HashMap<String, Integer>> accuracyConf = record.getAccuracy();
        if (accuracyConf == null || accuracyConf.isEmpty())
            return 0;

        return record.getAccuracy().values().stream()
                .map(entry -> entry.values().stream().reduce(0, Integer::sum))
                .reduce(Math::max).orElse(0);
    }

    private MyCacheManagerProvider getMyCacheManagerProvider() {
        return myCacheManagerProvider;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        log.log(Level.INFO, String.format("HotRod server %s:%d.", D2P_HOTROD_HOST, D2P_HOTROD_PORT));
        final FiddlerTool fiddler = new FiddlerTool(new MyCacheManagerProvider(D2P_HOTROD_HOST, D2P_HOTROD_PORT, D2P_HOTROD_CONN_TIMEOUT_S));
        fiddler.computeAccuracy(args[1]);
    }
}
