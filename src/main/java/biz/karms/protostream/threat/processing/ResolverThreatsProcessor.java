package biz.karms.protostream.threat.processing;

import biz.karms.Dump2Proto;
import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.protostream.threat.domain.*;
import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.protostream.threat.task.*;
import biz.karms.sinkit.ejb.cache.annotations.SinkitCacheName;
import biz.karms.sinkit.resolver.EndUserConfiguration;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import lombok.Setter;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;

/**
 * Processor which prepare all data needed to be exported and then export them.
 */
public class ResolverThreatsProcessor {

    private static final Logger logger = Logger.getLogger(ResolverThreatsProcessor.class.getName());

    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 100;

    private final int batchSize;
    private final RemoteCacheManager remoteCacheManagerForIndexedCaches;
    private final ConcurrentLinkedDeque<Integer> resolverIDs;
    private final ConcurrentLinkedDeque<Integer> clientIDs;
    private final ThreadPoolExecutor notificationExecutor;
    private final IoCKeeper ioCKeeper;

    public static Logger getLogger() {
        return logger;
    }

    @Setter
    private ResolverCacheExportTask<ByteBuffer> resolverCacheExportTask = new ResoverCacheFileExportTask();

    /**
     * Constructor creates this processor
     *
     * @param remoteCacheManagerForIndexedCaches remoteCacheManager which accesses indexed remote caches
     * @param batchSize                          resolvers' batch size (how many resolvers will be processed in a chunk)
     */
    public ResolverThreatsProcessor(final RemoteCacheManager remoteCacheManagerForIndexedCaches,
                                    final int batchSize,
                                    final ConcurrentLinkedDeque<Integer> resolverIDs,
                                    final ConcurrentLinkedDeque<Integer> clientIDs,
                                    final ThreadPoolExecutor notificationExecutor,
                                    final IoCKeeper ioCKeeper) {
        this.remoteCacheManagerForIndexedCaches = Objects
                .requireNonNull(remoteCacheManagerForIndexedCaches, "RemoteCacheManager for indexed caches cannot be null for processing");
        this.batchSize = batchSize < MIN_BATCH_SIZE ? MIN_BATCH_SIZE : (batchSize > MAX_BATCH_SIZE ? MAX_BATCH_SIZE : batchSize);
        this.resolverIDs = resolverIDs;
        this.clientIDs = clientIDs;
        this.notificationExecutor = notificationExecutor;
        this.ioCKeeper = Objects.requireNonNull(ioCKeeper, "IoCKeeper cannot be null for processing");
    }

    /**
     * Method generates resolvers' files
     *
     * @return boolean if the all resolvers have been exported
     */
    public boolean process() {
        final CompletableFuture<Boolean> processResolversFuture = CompletableFuture.completedFuture(new ProcessingContext())
                .thenApply(this::fetchResolverConfigurations)
                .thenApply(this::fetchEndUserConfigurations)
                .thenApply(this::fetchBlacklistedRecord)
                .thenApply(this::processResolvers);

        return processResolversFuture.join();
    }

    /**
     * Method returns all ResolverConfiguration data
     *
     * @param context processing context
     * @return updated processing context
     */
    ProcessingContext fetchResolverConfigurations(ProcessingContext context) {
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Entering fetchResolverConfigurations...");
        final long start = System.currentTimeMillis();
        final RemoteCache<Integer, ResolverConfiguration> resolverConfigurationCache = remoteCacheManagerForIndexedCaches
                .getCache(SinkitCacheName.resolver_configuration.name());

        //TODO: Do even/odd for resolver keys
        //TODO: Order by change
        final Set<Integer> keys;
        if ((resolverIDs == null || resolverIDs.isEmpty()) && (clientIDs == null || clientIDs.isEmpty())) {
            keys = resolverConfigurationCache.keySet();
            logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Getting all " + keys.size() + " Resolver IDs from cache and processing them...");
        } else {
            keys = new HashSet<>();

            // Process resolver IDs
            if (resolverIDs != null) {
                logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Using Resolver IDs collected from events.");
                Integer resolverID;
                // Why poll and not getting all? The collection is being modified concurrently, there might be records being added at this moment.
                while ((resolverID = resolverIDs.poll()) != null) {
                    keys.add(resolverID);
                }
                logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": " + keys.size() + " Resolver IDs collected.");
            }

            // Process client IDs
            if (clientIDs != null) {
                logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Using Client IDs collected from events.");
                Set<Integer> clientIDsToProcess = new HashSet<>();
                Integer clientID;
                // Why poll and not getting all? The collection is being modified concurrently, there might be records being added at this moment.
                while ((clientID = clientIDs.poll()) != null) {
                    clientIDsToProcess.add(clientID);
                }
                logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": " + clientIDsToProcess.size() + " Client IDs collected.");
                // We need to fetch all resolver IDs for these client IDs
                logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Converting Client IDs to Resolver IDs...");
                final QueryFactory qf = Search.getQueryFactory(resolverConfigurationCache);
                final Query query = qf.from(ResolverConfiguration.class)
                        .having("clientId").in(clientIDsToProcess)
                        .toBuilder()
                        .build();
                final List<ResolverConfiguration> resolverConfigurations = query.list();
                resolverConfigurations.forEach(c -> keys.add(c.getResolverId()));
                logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": " + keys.size() + " Resolver IDs collected.");
            }

            logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Working with " + keys.size() + " Resolver IDs that changed recently...");
        }

        final List<ResolverConfiguration> configurations;
        if (Dump2Proto.REVERSE_RESOLVERS_ORDER) {
            configurations = resolverConfigurationCache.getAll(keys).values().stream()
                    .sorted(Comparator.comparing(ResolverConfiguration::getResolverId).reversed()).collect(Collectors.toList());
        } else {
            configurations = resolverConfigurationCache.getAll(keys).values().stream()
                    .sorted(Comparator.comparing(ResolverConfiguration::getResolverId)).collect(Collectors.toList());
        }
        context.setResolverConfigurations(configurations);
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": fetchResolverConfigurations finished in " + (System.currentTimeMillis() - start) + " ms.");
        return context;
    }

    /**
     * Method returns all endUserConfiguration data
     *
     * @param context processing context
     * @return updated processing context
     */
    ProcessingContext fetchEndUserConfigurations(ProcessingContext context) {
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Entering fetchEndUserConfigurations...");
        final long start = System.currentTimeMillis();
        final RemoteCache<String, EndUserConfiguration> endUserConfigurationRemoteCache = remoteCacheManagerForIndexedCaches
                .getCache(SinkitCacheName.end_user_configuration.name());
        final Set<String> keys = endUserConfigurationRemoteCache.keySet();
        final Collection<EndUserConfiguration> endUserRecords = endUserConfigurationRemoteCache.getAll(keys).values();
        context.setEndUserRecords(endUserRecords);
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": fetchEndUserConfigurations finished in " + (System.currentTimeMillis() - start) + " ms.");
        return context;
    }

    /**
     * Method returns all blacklistedRecord data
     *
     * @param context processing context
     * @return updated processing context
     */
    ProcessingContext fetchBlacklistedRecord(ProcessingContext context) {
        context.setBlacklistedRecords(ioCKeeper.getBlacklistedRecords());
        return context;
    }

    /**
     * Method processes all resolvers stored in the processing context in batches (size is specified as parameter in generate method)
     *
     * @param context processing context
     * @return number of processed resolvers
     */
    boolean processResolvers(final ProcessingContext context) {
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Entering processResolvers...");
        final long start = System.currentTimeMillis();
        final List<ResolverConfiguration> allResolvers = new ArrayList<>(context.getResolverConfigurations());

        final int loops = (allResolvers.size() + this.batchSize - 1) / this.batchSize;
        final int countOfProcessedResolvers = IntStream.range(0, loops)
                .mapToObj(i -> allResolvers.subList(i * this.batchSize, Math.min(allResolvers.size(), (i + 1) * this.batchSize)))
                .map(batch -> processResolversBatch(batch, context))
                .reduce(0, Integer::sum);

        final boolean hasBeenAllResolversProcessed = allResolvers.size() == countOfProcessedResolvers;
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Resolvers have been processed " + (hasBeenAllResolversProcessed ? "successfully" : "unsuccessfully") + " in " + (System.currentTimeMillis() - start) + " ms.");
        return hasBeenAllResolversProcessed;
    }

    /**
     * Method processes batch of resolvers
     *
     * @param resolverConfigurations resolvers to be processed
     * @param context                processing context
     * @return number of processed resolvers
     */
    int processResolversBatch(final List<ResolverConfiguration> resolverConfigurations, final ProcessingContext context) {

        // Use parallel stream causes OOM - more threads handle the processing - bigger memory footprint
        // OOM git:24bf6838: at biz.karms.protostream.threat.processing.ResolverThreatsProcessor.lambda$processResolversBatch$7(ResolverThreatsProcessor.java:261)
        // single stream is slower, lower memory footprint
        return resolverConfigurations.stream().map(resolverConfiguration -> {
            // users custom list task
            final UserCustomListTask userCustomListTask = new UserCustomListTask(resolverConfiguration, context);
            final CompletableFuture<List<CustomListRecord>> userCustomListRecordsFuture = CompletableFuture.supplyAsync(userCustomListTask::processData);

            // ip ranges task
            final ResolverConfigurationIpRangesTask resolverConfigurationIpRangesTask = new ResolverConfigurationIpRangesTask(resolverConfiguration, context);
            final CompletableFuture<List<IpRangesRecord>> ipRangesRecordsFuture = CompletableFuture.supplyAsync(resolverConfigurationIpRangesTask::processData);

            // policies task
            final ResolverConfigurationPolicyTask resolverConfigurationPolicyTask = new ResolverConfigurationPolicyTask(resolverConfiguration, context);
            final CompletableFuture<List<PolicyRecord>> policyRecordsFuture = CompletableFuture.supplyAsync(resolverConfigurationPolicyTask::processData);

            // resolvers's threats
            final ResolverThreatTask resolverThreatTask = new ResolverThreatTask(resolverConfiguration, context);
            final CompletableFuture<List<Threat>> threatRecordsFuture = CompletableFuture
                    .supplyAsync(resolverThreatTask::processData)
                    .thenApplyAsync(resolverThreatTask::postProcessData);

            final AtomicBoolean isPassed = new AtomicBoolean(true);

            // run and wait till all task are done
            CompletableFuture.allOf(userCustomListRecordsFuture, ipRangesRecordsFuture, policyRecordsFuture, threatRecordsFuture)
                    .exceptionally(e -> handleException(e, isPassed))
                    .join();

            // if any of subtask has failed - return as uncompleted
            if (isPassed.get()) {
                // fetch their results and put them into resolver record envelope
                final ResolverRecord resolverRecord = new ResolverRecord();
                resolverRecord.setResolverId(resolverConfiguration.getResolverId());
                resolverRecord.setThreats(threatRecordsFuture.join());
                resolverRecord.setIpRangesRecords(ipRangesRecordsFuture.join());
                resolverRecord.setPolicyRecords(policyRecordsFuture.join());
                resolverRecord.setCustomListRecords(userCustomListRecordsFuture.join());

                // transform into protostream and export them by registered exporter
                CompletableFuture.completedFuture(resolverRecord)
                        .thenApply(record -> new ProtostreamTransformerTask(resolverConfiguration).transformToProtobuf(record))
                        .thenApply(data -> {
                            resolverCacheExportTask.export(resolverConfiguration, data, notificationExecutor);
                            return null;
                        })
                        .exceptionally(e -> handleException(e, isPassed))
                        .join();
            }

            return isPassed.get() ? 1 : 0;
        }).reduce(0, Integer::sum);
    }

    Void handleException(Throwable e, AtomicBoolean holder) {
        holder.set(false);
        if (e instanceof ResolverProcessingException || e.getCause() instanceof ResolverProcessingException) {
            final ResolverProcessingException processingException = (ResolverProcessingException) (e instanceof ResolverProcessingException ? e : e.getCause());
            final ResolverProcessingTask failedTask = processingException.getTask();
            final ResolverConfiguration resolverConfiguration = processingException.getResolverConfiguration();
            logger.log(Level.SEVERE,
                    format("Unable to finish exporting resolver(#'%s')'s data, because processed subtask '%s' has failed due to",
                            resolverConfiguration.getResolverId(), failedTask), e.getCause());
        } else {
            logger.log(Level.SEVERE, "Unable to finish exporting resolver's data, because something went wrong", e);
        }
        return null;
    }
}
