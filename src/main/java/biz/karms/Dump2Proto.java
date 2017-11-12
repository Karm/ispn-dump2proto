package biz.karms;

import biz.karms.protostream.CustomlistProtostreamGenerator;
import biz.karms.protostream.IoCWithCustomProtostreamGenerator;
import biz.karms.protostream.IocProtostreamGenerator;
import biz.karms.protostream.WhitelistProtostreamGenerator;
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
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Michal Karm Babacek
 */
public class Dump2Proto {

    protected static final Logger log = Logger.getLogger(Dump2Proto.class.getName());

    public static final String GENERATED_PROTOFILES_DIRECTORY =
            (System.getenv().containsKey("SINKIT_GENERATED_PROTOFILES_DIRECTORY") && StringUtils.isNotEmpty(System.getenv("SINKIT_GENERATED_PROTOFILES_DIRECTORY")))
                    ? System.getenv("SINKIT_GENERATED_PROTOFILES_DIRECTORY") : System.getProperty("java.io.tmpdir");
    public static final Set<OpenOption> options = Stream.of(APPEND, CREATE).collect(Collectors.toSet());
    public static final FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r-----"));
    public static final String SINKIT_CACHE_PROTOBUF = "/sinkitprotobuf/sinkit-cache.proto";

    // If host or port are nto set, the app fails to start.
    private static final String SINKIT_HOTROD_HOST = System.getenv("SINKIT_HOTROD_HOST");
    private static final int SINKIT_HOTROD_PORT = Integer.parseInt(System.getenv("SINKIT_HOTROD_PORT"));
    private static final long SINKIT_HOTROD_CONN_TIMEOUT_S = (System.getenv().containsKey("SINKIT_HOTROD_CONN_TIMEOUT_S")) ?
            Integer.parseInt(System.getenv("SINKIT_HOTROD_CONN_TIMEOUT_S")) :
            300;

    /**
     * Scheduling
     */
    private static final int MIN_DELAY_BEFORE_START_S = 10;
    private static final int MAX_DELAY_BEFORE_START_S = 60;

    /**
     * 5 - 10 minutes is a sane value, i.e. 300s
     */
    private static final long SINKIT_CUSTOMLIST_GENERATOR_INTERVAL_S = Integer.parseInt(System.getenv("SINKIT_CUSTOMLIST_GENERATOR_INTERVAL_S"));

    /**
     * cca 4 hours could be a good interval, i.e. 14400s
     */
    private static final long SINKIT_IOC_GENERATOR_INTERVAL_S = Integer.parseInt(System.getenv("SINKIT_IOC_GENERATOR_INTERVAL_S"));

    /**
     * cca 1 hour could be a good interval, i.e. 3600s
     */
    private static final long SINKIT_ALL_IOC_GENERATOR_INTERVAL_S = Integer.parseInt(System.getenv("SINKIT_ALL_IOC_GENERATOR_INTERVAL_S"));

    /**
     * cca 2 minutes could be a good interval, i.e. 120s
     */
    private static final long SINKIT_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S = Integer.parseInt(System.getenv("SINKIT_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S"));

    /**
     * cca 12 hours is O.K., i.e. 43200s
     */
    private static final long SINKIT_WHITELIST_GENERATOR_INTERVAL_S = Integer.parseInt(System.getenv("SINKIT_WHITELIST_GENERATOR_INTERVAL_S"));

    /**
     * corePoolSize: 1, The idea is that we prefer the tasks being randomly delayed by one another rather than having them executed simultaneously.
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    //private final ScheduledExecutorService customListGeneratorScheduler = Executors.newScheduledThreadPool(1);
    //private final ScheduledExecutorService iocGeneratorScheduler = Executors.newScheduledThreadPool(1);
    //private final ScheduledExecutorService iocWithCustomlistGeneratorScheduler = Executors.newScheduledThreadPool(1);
    //private final ScheduledExecutorService whitelistGeneratorScheduler = Executors.newScheduledThreadPool(1);

    private final MyCacheManagerProvider myCacheManagerProvider;

    private final ShutdownHook jvmShutdownHook;

    private final ScheduledFuture<?> customListGeneratorHandle;
    private final ScheduledFuture<?> iocGeneratorHandle;
    private final ScheduledFuture<?> allIocWithCustomlistGeneratorHandle;
    private final ScheduledFuture<?> whitelistGeneratorHandle;
    private final ScheduledFuture<?> allCustomlistGeneratorHandle;

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

    public Dump2Proto(final MyCacheManagerProvider myCacheManagerProvider) {
        this.myCacheManagerProvider = myCacheManagerProvider;
        this.jvmShutdownHook = new ShutdownHook(myCacheManagerProvider);
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);

        if (SINKIT_ALL_IOC_GENERATOR_INTERVAL_S > 0) {
            this.allIocWithCustomlistGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new IoCWithCustomProtostreamGenerator(myCacheManagerProvider.getCacheManagerForIndexableCaches(),
                                    myCacheManagerProvider.getBlacklistCache(), IoCWithCustomProtostreamGenerator.SCOPE.ALL),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            SINKIT_ALL_IOC_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.allIocWithCustomlistGeneratorHandle = null;
        }

        if (SINKIT_CUSTOMLIST_GENERATOR_INTERVAL_S > 0) {
            this.customListGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new CustomlistProtostreamGenerator(myCacheManagerProvider.getCacheManagerForIndexableCaches()),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            SINKIT_CUSTOMLIST_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.customListGeneratorHandle = null;
        }

        if (SINKIT_IOC_GENERATOR_INTERVAL_S > 0) {
            this.iocGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new IocProtostreamGenerator(myCacheManagerProvider.getCacheManagerForIndexableCaches(),
                                    myCacheManagerProvider.getBlacklistCache()),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            SINKIT_IOC_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.iocGeneratorHandle = null;
        }


        if (SINKIT_WHITELIST_GENERATOR_INTERVAL_S > 0) {
            this.whitelistGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new WhitelistProtostreamGenerator(myCacheManagerProvider.getWhitelistCache()),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            SINKIT_WHITELIST_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.whitelistGeneratorHandle = null;
        }

        if (SINKIT_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S > 0) {
            this.allCustomlistGeneratorHandle = scheduler
                    .scheduleAtFixedRate(new IoCWithCustomProtostreamGenerator(myCacheManagerProvider.getCacheManagerForIndexableCaches(),
                                    myCacheManagerProvider.getBlacklistCache(), IoCWithCustomProtostreamGenerator.SCOPE.CUSTOM_LISTS_ONLY),
                            (new Random()).nextInt((MAX_DELAY_BEFORE_START_S - MIN_DELAY_BEFORE_START_S) + 1) + MIN_DELAY_BEFORE_START_S,
                            SINKIT_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S, SECONDS);
        } else {
            this.allCustomlistGeneratorHandle = null;
        }
    }

    public void cancelAll() {
        if(customListGeneratorHandle != null) {
            customListGeneratorHandle.cancel(true);
        }
        if(iocGeneratorHandle != null) {
            iocGeneratorHandle.cancel(true);
        }
        if(allIocWithCustomlistGeneratorHandle != null) {
            allIocWithCustomlistGeneratorHandle.cancel(true);
        }
        if(whitelistGeneratorHandle != null) {
            whitelistGeneratorHandle.cancel(true);
        }
        if(allCustomlistGeneratorHandle != null) {
            allCustomlistGeneratorHandle.cancel(true);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final Dump2Proto dump2Proto = new Dump2Proto(new MyCacheManagerProvider(SINKIT_HOTROD_HOST, SINKIT_HOTROD_PORT, SINKIT_HOTROD_CONN_TIMEOUT_S));
    }

}
