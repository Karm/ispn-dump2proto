package biz.karms;

import biz.karms.protostream.*;
import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.sinkit.ejb.cache.annotations.SinkitCacheName;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Michal Karm Babacek
 */
public class Dump2Proto {

    protected static final Logger log = Logger.getLogger(Dump2Proto.class.getName());

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
     * 5 - 10 minutes is a sane value, i.e. 300s
     */
    private static final long D2P_CUSTOMLIST_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_CUSTOMLIST_GENERATOR_INTERVAL_S", "0"));

    /**
     * cca 4 hours could be a good interval, i.e. 14400s
     */
    private static final long D2P_IOC_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_IOC_GENERATOR_INTERVAL_S", "0"));

    /**
     * cca 2 hours could be a good interval, i.e. 7200s
     */
    private static final long D2P_ALL_IOC_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_ALL_IOC_GENERATOR_INTERVAL_S", "0"));

    /**
     * cca 2 minutes could be a good interval, i.e. 120s
     */
    private static final long D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S", "0"));

    /**
     * cca 12 hours is O.K., i.e. 43200s
     */
    private static final long D2P_WHITELIST_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_WHITELIST_GENERATOR_INTERVAL_S", "0"));


    /**
     * Resolver generator interval
     */
    private static final long D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S", "0"));
    private static final long D2P_RESOLVER_CACHE_LISTENER_GENERATOR_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_RESOLVER_CACHE_LISTENER_GENERATOR_INTERVAL_S", "0"));
    private static final int D2P_RESOLVER_CACHE_BATCH_SIZE_S = Integer.parseInt(System.getProperty("D2P_RESOLVER_CACHE_BATCH_SIZE_S", "20"));

    /**
     * Cache backup, IoC dumper
     */
    private static final long D2P_IOC_DUMPER_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_IOC_DUMPER_INTERVAL_S", "0"));

    /**
     * S3 and disk storage
     */
    public static final String GENERATED_PROTOFILES_DIRECTORY =
            (System.getProperties().containsKey("D2P_GENERATED_PROTOFILES_DIRECTORY") && StringUtils.isNotEmpty(System.getProperty("D2P_GENERATED_PROTOFILES_DIRECTORY")))
                    ? System.getProperty("D2P_GENERATED_PROTOFILES_DIRECTORY") : System.getProperty("java.io.tmpdir");
    public static final Set<OpenOption> options = Stream.of(APPEND, CREATE).collect(Collectors.toSet());
    public static final FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-"));
    public static final Boolean USE_S3_ONLY = Boolean.parseBoolean(System.getProperty("D2P_USE_S3_ONLY"));
    public static final String S3_ENDPOINT = System.getProperty("D2P_S3_ENDPOINT");
    public static final String S3_ACCESS_KEY = System.getProperty("D2P_S3_ACCESS_KEY");
    public static final String S3_SECRET_KEY = System.getProperty("D2P_S3_SECRET_KEY");
    public static final String S3_BUCKET_NAME = System.getProperty("D2P_S3_BUCKET_NAME");
    public static final String S3_REGION = System.getProperty("D2P_S3_REGION");

    /**
     * Notification endpoint
     */
    public static final String D2P_NOTIFICATION_ENDPOINT_TEMPLATE = System.getProperty("D2P_NOTIFICATION_ENDPOINT_TEMPLATE",
            "http://wsproxy_adddress:8080/wsproxy/rest/message/%d/updatecache");
    public static final String D2P_NOTIFICATION_ENDPOINT_METHOD = System.getProperty("D2P_NOTIFICATION_ENDPOINT_METHOD",
            "POST");
    public static final int D2P_NOTIFICATION_ENDPOINT_TIMEOUT_MS = Integer.parseInt(System.getProperty("D2P_NOTIFICATION_ENDPOINT_TIMEOUT_MS", "2000"));
    public static final boolean D2P_USE_NOTIFICATION_ENDPOINT = Boolean.parseBoolean(System.getProperty("D2P_USE_NOTIFICATION_ENDPOINT", "False"));


    /**
     * Resolver listeners and work partitioning
     */
    public static final Boolean ENABLE_CACHE_LISTENERS = Boolean.parseBoolean(System.getProperty("D2P_ENABLE_CACHE_LISTENERS"));
    public static final Boolean REVERSE_RESOLVERS_ORDER = Boolean.parseBoolean(System.getProperty("D2P_REVERSE_RESOLVERS_ORDER"));

    /**
     * IoCs, millions of them, are refreshed only once in a while
     */
    private static final long D2P_IOC_REFRESH_INTERVAL_S = Integer.parseInt(System.getProperty("D2P_IOC_REFRESH_INTERVAL_S", "600"));

    /**
     * corePoolSize: 1, The idea is that we prefer the tasks being randomly delayed by one another rather than having them executed simultaneously.
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final ScheduledExecutorService priorityScheduler = Executors.newScheduledThreadPool(5);
    private final ScheduledExecutorService iocKeeperScheduler = Executors.newScheduledThreadPool(1);

    /**
     * Used to send HTTP notifications to a REST API
     */
    private final ThreadPoolExecutor notificationExecutor;

    private final MyCacheManagerProvider myCacheManagerProvider;

    private final ShutdownHook jvmShutdownHook;

    private final ScheduledFuture<?> customListGeneratorHandle;
    private final ScheduledFuture<?> iocGeneratorHandle;
    private final ScheduledFuture<?> allIocWithCustomlistGeneratorHandle;
    private final ScheduledFuture<?> resolverCacheGeneratorHandle;
    private final ScheduledFuture<?> resolverCacheListenerGeneratorHandle;
    private final ScheduledFuture<?> whitelistGeneratorHandle;
    private final ScheduledFuture<?> allCustomlistGeneratorHandle;
    private final ScheduledFuture<?> iocDumperHandle;
    private final ScheduledFuture<?> iocKeeperHandle;

    private final ConcurrentLinkedDeque<Integer> resolverIDs;
    private final ConcurrentLinkedDeque<Integer> clientIDs;

    private static class ShutdownHook extends Thread {
        private final MyCacheManagerProvider myCacheManagerProvider;

        ShutdownHook(final MyCacheManagerProvider myCacheManagerProvider) {
            this.myCacheManagerProvider = myCacheManagerProvider;
        }

        public void run() {
            log.info("Thread " + Thread.currentThread().getName() + ": Shutting down.");
            myCacheManagerProvider.destroy();
        }
    }

    private Dump2Proto(final MyCacheManagerProvider myCacheManagerProvider) {

        if (D2P_USE_NOTIFICATION_ENDPOINT) {
            notificationExecutor = new ThreadPoolExecutor(5, 60, 60, SECONDS, new LinkedBlockingQueue<>());
        } else {
            notificationExecutor = null;
        }

        //TODO: Validation

        this.myCacheManagerProvider = myCacheManagerProvider;
        this.jvmShutdownHook = new ShutdownHook(myCacheManagerProvider);
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);

        if (ENABLE_CACHE_LISTENERS) {
            resolverIDs = new ConcurrentLinkedDeque<>();
            clientIDs = new ConcurrentLinkedDeque<>();
            myCacheManagerProvider.getCacheManagerForIndexableCaches().getCache(SinkitCacheName.resolver_configuration.name())
                    .addClientListener(new ResolverCacheUpdateListener(resolverIDs));
            myCacheManagerProvider.getCacheManagerForIndexableCaches().getCache(SinkitCacheName.end_user_configuration.name())
                    .addClientListener(new EndUserCacheUpdateListener(clientIDs));
        } else {
            resolverIDs = null;
            clientIDs = null;
        }

        final IoCKeeper ioCKeeper = IoCKeeper.getIoCKeeper(myCacheManagerProvider.getCacheManager());

        this.iocKeeperHandle = iocKeeperScheduler
                .scheduleAtFixedRate(
                        ioCKeeper,
                        0,
                        D2P_IOC_REFRESH_INTERVAL_S, SECONDS);

        if (D2P_RESOLVER_CACHE_LISTENER_GENERATOR_INTERVAL_S > 0) {
            this.resolverCacheListenerGeneratorHandle = priorityScheduler
                    .scheduleAtFixedRate(
                            new ResolverThreatsGenerator(
                                    myCacheManagerProvider.getCacheManagerForIndexableCaches(),
                                    D2P_RESOLVER_CACHE_BATCH_SIZE_S,
                                    resolverIDs,
                                    clientIDs,
                                    notificationExecutor,
                                    ioCKeeper),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_RESOLVER_CACHE_LISTENER_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.resolverCacheListenerGeneratorHandle = null;
        }

        if (D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S > 0) {
            this.resolverCacheGeneratorHandle = scheduler
                    .scheduleAtFixedRate(
                            new ResolverThreatsGenerator(
                                    myCacheManagerProvider.getCacheManagerForIndexableCaches(),
                                    D2P_RESOLVER_CACHE_BATCH_SIZE_S,
                                    null,
                                    null,
                                    notificationExecutor,
                                    ioCKeeper),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.resolverCacheGeneratorHandle = null;
        }

        if (D2P_IOC_DUMPER_INTERVAL_S > 0) {
            this.iocDumperHandle = scheduler
                    .scheduleAtFixedRate(new IoCDumper(ioCKeeper),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_IOC_DUMPER_INTERVAL_S, SECONDS);
        } else {
            this.iocDumperHandle = null;
        }

        if (D2P_ALL_IOC_GENERATOR_INTERVAL_S > 0) {
            this.allIocWithCustomlistGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new IoCWithCustomProtostreamGenerator(
                                    myCacheManagerProvider.getCacheManagerForIndexableCaches(),
                                    IoCWithCustomProtostreamGenerator.SCOPE.ALL,
                                    ioCKeeper),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_ALL_IOC_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.allIocWithCustomlistGeneratorHandle = null;
        }

        if (D2P_CUSTOMLIST_GENERATOR_INTERVAL_S > 0) {
            this.customListGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new CustomlistProtostreamGenerator(myCacheManagerProvider.getCacheManagerForIndexableCaches()),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_CUSTOMLIST_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.customListGeneratorHandle = null;
        }

        if (D2P_IOC_GENERATOR_INTERVAL_S > 0) {
            this.iocGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new IocProtostreamGenerator(myCacheManagerProvider.getCacheManagerForIndexableCaches(), ioCKeeper),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_IOC_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.iocGeneratorHandle = null;
        }

        if (D2P_WHITELIST_GENERATOR_INTERVAL_S > 0) {
            this.whitelistGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new WhitelistProtostreamGenerator(myCacheManagerProvider.getWhitelistCache()),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_WHITELIST_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.whitelistGeneratorHandle = null;
        }

        if (D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S > 0) {
            this.allCustomlistGeneratorHandle = scheduler
                    .scheduleAtFixedRate(
                            new IoCWithCustomProtostreamGenerator(myCacheManagerProvider.getCacheManagerForIndexableCaches(),
                                    IoCWithCustomProtostreamGenerator.SCOPE.CUSTOM_LISTS_ONLY, ioCKeeper),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.allCustomlistGeneratorHandle = null;
        }
    }

    public void cancelAll() {
        if (iocGeneratorHandle != null) {
            iocGeneratorHandle.cancel(true);
        }
        if (resolverCacheGeneratorHandle != null) {
            resolverCacheGeneratorHandle.cancel(true);
        }
        if (customListGeneratorHandle != null) {
            customListGeneratorHandle.cancel(true);
        }

        if (allIocWithCustomlistGeneratorHandle != null) {
            allIocWithCustomlistGeneratorHandle.cancel(true);
        }
        if (whitelistGeneratorHandle != null) {
            whitelistGeneratorHandle.cancel(true);
        }
        if (allCustomlistGeneratorHandle != null) {
            allCustomlistGeneratorHandle.cancel(true);
        }
        if (iocDumperHandle != null) {
            iocDumperHandle.cancel(true);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        log.info("Thread " + Thread.currentThread().getName() + ": D2P_HOTROD_HOST: " + D2P_HOTROD_HOST);
        final Dump2Proto dump2Proto = new Dump2Proto(new MyCacheManagerProvider(D2P_HOTROD_HOST, D2P_HOTROD_PORT, D2P_HOTROD_CONN_TIMEOUT_S));
    }

}
