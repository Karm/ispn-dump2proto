package biz.karms;

import biz.karms.protostream.IoCDumper;
import biz.karms.protostream.ResolverThreatsGenerator;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Michal Karm Babacek
 */
public class Dump2Proto {

    protected static final Logger log = Logger.getLogger(Dump2Proto.class.getName());

    public static final String GENERATED_PROTOFILES_DIRECTORY =
            (System.getProperties().containsKey("D2P_GENERATED_PROTOFILES_DIRECTORY") && StringUtils.isNotEmpty(System.getProperty("D2P_GENERATED_PROTOFILES_DIRECTORY")))
                    ? System.getProperty("D2P_GENERATED_PROTOFILES_DIRECTORY") : System.getProperty("java.io.tmpdir");
    public static final Set<OpenOption> options = Stream.of(TRUNCATE_EXISTING, CREATE).collect(Collectors.toSet());
    public static final FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-"));
    public static final String D2P_CACHE_PROTOBUF = "/sinkitprotobuf/sinkit-cache.proto";

    // If host or port are nto set, the app fails to start.
    private static final String D2P_HOTROD_HOST = System.getProperty("D2P_HOTROD_HOST");
    private static final int D2P_HOTROD_PORT = Integer.parseInt(System.getProperty("D2P_HOTROD_PORT", "11322"));
    private static final long D2P_HOTROD_CONN_TIMEOUT_S = (System.getProperties().containsKey("D2P_HOTROD_CONN_TIMEOUT_S")) ?
            Integer.parseInt(System.getProperty("D2P_HOTROD_CONN_TIMEOUT_S")) :
            300;

    /**
     * Scheduling
     */
    private static final int MIN_DELAY_BEFORE_START_S = 60;
    private static final int MAX_DELAY_BEFORE_START_S = 240;

    /**
     * Resolver generator interval
     */
    private static final long D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S", "0"));
    private static final int D2P_RESOLVER_CACHE_BATCH_SIZE_S = Integer.parseInt(System.getProperty("D2P_RESOLVER_CACHE_BATCH_SIZE_S", "20"));

    /**
     * Cache backup, IoC dumper
     */
    private static final long D2P_IOC_DUMPER_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_IOC_DUMPER_INTERVAL_S", "0"));


    /**
     * corePoolSize: 1, The idea is that we prefer the tasks being randomly delayed by one another rather than having them executed simultaneously.
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final MyCacheManagerProvider myCacheManagerProvider;

    private final ShutdownHook jvmShutdownHook;

    private final ScheduledFuture<?> resolverCacheGeneratorHandle;
    private final ScheduledFuture<?> iocDumperHandle;

    private static class ShutdownHook extends Thread {
        private final MyCacheManagerProvider myCacheManagerProvider;

        ShutdownHook(final MyCacheManagerProvider myCacheManagerProvider) {
            this.myCacheManagerProvider = myCacheManagerProvider;
        }

        public void run() {
            log.info("Shutting down.");
            myCacheManagerProvider.destroy();
        }
    }

    private Dump2Proto(final MyCacheManagerProvider myCacheManagerProvider) {

        //TODO: Validation

        this.myCacheManagerProvider = myCacheManagerProvider;
        this.jvmShutdownHook = new ShutdownHook(myCacheManagerProvider);
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);

        if (D2P_IOC_DUMPER_INTERVAL_S > 0) {
            this.iocDumperHandle = scheduler
                    .scheduleAtFixedRate(new IoCDumper(myCacheManagerProvider.getBlacklistCache()),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_IOC_DUMPER_INTERVAL_S, SECONDS);
        } else {
            this.iocDumperHandle = null;
        }

        if (D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S > 0) {
            this.resolverCacheGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new ResolverThreatsGenerator(myCacheManagerProvider.getCacheManager(), myCacheManagerProvider.getCacheManagerForIndexableCaches(), D2P_RESOLVER_CACHE_BATCH_SIZE_S),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.resolverCacheGeneratorHandle = null;
        }
    }

    public void cancelAll() {
        if (resolverCacheGeneratorHandle != null) {
            resolverCacheGeneratorHandle.cancel(true);
        }
        if (iocDumperHandle != null) {
            iocDumperHandle.cancel(true);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        log.info("D2P_HOTROD_HOST: " + D2P_HOTROD_HOST);
        final Dump2Proto dump2Proto = new Dump2Proto(new MyCacheManagerProvider(D2P_HOTROD_HOST, D2P_HOTROD_PORT, D2P_HOTROD_CONN_TIMEOUT_S));
    }

}
