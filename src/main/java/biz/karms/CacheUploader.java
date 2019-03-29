package biz.karms;

import biz.karms.protostream.IoCDumper;
import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.protostream.ioc.marshallers.*;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static biz.karms.protostream.IoCDumper.BLACKLISTED_RECORD_PROTOBUF;
import static java.lang.Math.min;

/**
 * @author Michal Karm Babacek
 */
public class CacheUploader {
    private static final String D2P_HOTROD_HOST = System.getProperty("D2P_HOTROD_HOST");
    private static final int D2P_HOTROD_PORT = Integer.parseInt(System.getProperty("D2P_HOTROD_PORT", "11322"));
    private static final long D2P_HOTROD_CONN_TIMEOUT_S = (System.getProperties().containsKey("D2P_HOTROD_CONN_TIMEOUT_S")) ?
            Integer.parseInt(System.getProperty("D2P_HOTROD_CONN_TIMEOUT_S")) :
            300;

    private static final int BUCKET_SIZE = 2_000;

    private static final Logger log = Logger.getLogger(CacheUploader.class.getName());

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

    private CacheUploader(final MyCacheManagerProvider myCacheManagerProvider) {
        this.myCacheManagerProvider = myCacheManagerProvider;
        this.jvmShutdownHook = new ShutdownHook(myCacheManagerProvider);
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);
    }

    public void upload(final File protobuffer) {
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Cache upload processing started.");


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

        if (protobuffer.exists()) {
            try (InputStream is = new FileInputStream(protobuffer)) {
                final AtomicBoolean running = new AtomicBoolean(true);
                Runnable spinner = () -> {
                    final Random rng = new Random();
                    while (running.get()) {
                        System.out.print("\033[1K\033[1G");
                        System.out.print("Loading protobuffer... ");
                        for (int i = 0; i < rng.nextInt(80); i++) System.out.print("▍");
                        System.out.flush();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                };
                new Thread(spinner).start();
                records = ProtobufUtil.readFrom(ctx, is, ArrayList.class);
                running.set(false);
            } catch (IOException e) {
                e.printStackTrace();
                log.log(Level.SEVERE, String.format("%s being empty / non-deserializable is unexpected. Aborting.", protobuffer.getAbsolutePath()));
                return;
            }
            System.out.print(System.lineSeparator());
            log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Deserialization of " + records.size() + " records took " + (System.currentTimeMillis() - start) + " ms.");
        } else {
            log.log(Level.SEVERE, String.format("%s does not exist. Aborting.", protobuffer.getAbsolutePath()));
            return;
        }

        start = System.currentTimeMillis();

        final RemoteCache<String, BlacklistedRecord> blacklistedCache = myCacheManagerProvider.getBlacklistCache();

        int c = 0;
        int rSize = records.size();
        final int numOfBuckets = (rSize % BUCKET_SIZE == 0) ? rSize / BUCKET_SIZE : rSize / BUCKET_SIZE + 1;
        System.out.print(System.lineSeparator());
        for (int iteration = 0; iteration < numOfBuckets; iteration++) {
            int toTake = min(records.size() - c, BUCKET_SIZE);
            final List<BlacklistedRecord> subList = records.subList(c, c + toTake);
            c += toTake;
            System.out.print("\033[1K\033[1G");
            System.out.print(String.format("%d/%d ", c, rSize));
            for (int i = 0; i < (c + iteration) % 80; i++) System.out.print("▍");
            System.out.flush();
            blacklistedCache.putAll(subList.stream().collect(Collectors.toMap(BlacklistedRecord::getBlackListedDomainOrIP, Function.identity())));
        }
        System.out.print(System.lineSeparator());
        log.log(Level.INFO, String.format("Upload of %d records took %d ms.", records.size(), (System.currentTimeMillis() - start)));
    }

    private MyCacheManagerProvider getMyCacheManagerProvider() {
        return myCacheManagerProvider;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        log.log(Level.INFO, String.format("HotRod server %s:%d.", D2P_HOTROD_HOST, D2P_HOTROD_PORT));
        final CacheUploader cacheUploader = new CacheUploader(new MyCacheManagerProvider(D2P_HOTROD_HOST, D2P_HOTROD_PORT, D2P_HOTROD_CONN_TIMEOUT_S));
        if ("-u".equals(args[0])) {
            //TODO: Validate input
            cacheUploader.upload(new File(args[1]));
        } else if ("-d".equals(args[0])) {
            //TODO: use filepath
            final IoCKeeper ioCKeeper = IoCKeeper.getIoCKeeper(cacheUploader.getMyCacheManagerProvider().getCacheManager());
            final Thread tKeeper = new Thread(ioCKeeper);
            tKeeper.start();
            tKeeper.join();
            final Thread tDumper = new Thread(new IoCDumper(ioCKeeper));
            tDumper.start();
            tDumper.join();
        } else {
            log.log(Level.SEVERE, String.format("Command %s is unknown. Use -d for dump and -u for upload.", args[0]));
        }
    }
}
