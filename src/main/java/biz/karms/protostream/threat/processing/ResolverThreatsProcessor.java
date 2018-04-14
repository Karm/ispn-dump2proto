package biz.karms.protostream.threat.processing;

import biz.karms.protostream.threat.domain.CustomListRecord;
import biz.karms.protostream.threat.domain.IpRangesRecord;
import biz.karms.protostream.threat.domain.PolicyRecord;
import biz.karms.protostream.threat.domain.ResolverRecord;
import biz.karms.protostream.threat.domain.Threat;
import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.protostream.threat.task.ProtostreamTransformerTask;
import biz.karms.protostream.threat.task.ResolverCacheExportTask;
import biz.karms.protostream.threat.task.ResolverConfigurationIpRangesTask;
import biz.karms.protostream.threat.task.ResolverConfigurationPolicyTask;
import biz.karms.protostream.threat.task.ResolverProcessingTask;
import biz.karms.protostream.threat.task.ResolverThreatTask;
import biz.karms.protostream.threat.task.ResoverCacheFileExportTask;
import biz.karms.protostream.threat.task.UserCustomListTask;
import biz.karms.sinkit.ejb.cache.annotations.SinkitCacheName;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.resolver.EndUserConfiguration;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import infinispan.org.jboss.as.protocol.mgmt.RequestProcessingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import lombok.Setter;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.util.CloseableIterator;

import static java.lang.String.format;

/**
 * Processor which prepare all data needed to be exported and then export them.
 */
public class ResolverThreatsProcessor {

    private static final Logger logger = Logger.getLogger(ResolverThreatsProcessor.class.getName());

    private static final int MIN_BATCH_SIZE = 10;
    private static final int MAX_BATCH_SIZE = 100;

    private final int batchSize;
    private final RemoteCacheManager remoteCacheManager;
    private final RemoteCacheManager remoteCacheManagerForIndexedCaches;

    public static Logger getLogger() {
        return logger;
    }

    @Setter
    private ResolverCacheExportTask<ByteBuffer> resolverCacheExportTask = new ResoverCacheFileExportTask();

    /**
     * Constructor creates this processor
     * @param remoteCacheManager remoteCacheManager which accesses remote caches
     * @param remoteCacheManagerForIndexedCaches remoteCacheManager which accesses indexed remote caches
     * @param batchSize resolvers' batch size (how many resolvers will be processed in a chunk)
     */
    public ResolverThreatsProcessor(RemoteCacheManager remoteCacheManager, RemoteCacheManager remoteCacheManagerForIndexedCaches, int batchSize) {
        this.remoteCacheManager = Objects.requireNonNull(remoteCacheManager, "RemoteCacheManager cannot be null for processing");
        this.remoteCacheManagerForIndexedCaches = Objects
                .requireNonNull(remoteCacheManagerForIndexedCaches, "RemoteCacheManager for indexed caches cannot be null for processing");
        this.batchSize = batchSize < MIN_BATCH_SIZE ? MIN_BATCH_SIZE : (batchSize > MAX_BATCH_SIZE ? MAX_BATCH_SIZE : batchSize);
    }

    /**
     * Method generates resolvers' files
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
     * @param context processing context
     * @return updated processing context
     */
    ProcessingContext fetchResolverConfigurations(ProcessingContext context) {
        final RemoteCache<Integer, ResolverConfiguration> resolverConfigurationCache = remoteCacheManagerForIndexedCaches
                .getCache(SinkitCacheName.resolver_configuration.name());

        final Set<Integer> keys = resolverConfigurationCache.keySet();
        final Collection<ResolverConfiguration> configurations = resolverConfigurationCache.getAll(keys).values();
        context.setResolverConfigurations(configurations);
        return context;
    }

    /**
     * Method returns all endUserConfiguration data
     * @param context processing context
     * @return updated processing context
     */
    ProcessingContext fetchEndUserConfigurations(ProcessingContext context) {
        final RemoteCache<String, EndUserConfiguration> endUserConfigurationRemoteCache = remoteCacheManagerForIndexedCaches
                .getCache(SinkitCacheName.end_user_configuration.name());

        final Set<String> keys = endUserConfigurationRemoteCache.keySet();
        final Collection<EndUserConfiguration> endUserRecords = endUserConfigurationRemoteCache.getAll(keys).values();
        context.setEndUserRecords(endUserRecords);
        return context;
    }

    /**
     * Method returns all blacklistedRecord data
     * @param context processing context
     * @return updated processing context
     */
    ProcessingContext fetchBlacklistedRecord(ProcessingContext context) {
        final RemoteCache<String, BlacklistedRecord> blacklistedRecordRemoteCache = remoteCacheManager.getCache(SinkitCacheName.infinispan_blacklist.name());

        final Collection<BlacklistedRecord> blacklistedRecords = new ArrayList<>();
        try (CloseableIterator<Map.Entry<Object, Object>> it = blacklistedRecordRemoteCache.retrieveEntries(null, 1000)) {
            while (it.hasNext()) {
                blacklistedRecords.add((BlacklistedRecord) it.next().getValue());
            }
        }
        context.setBlacklistedRecords(blacklistedRecords);
        return context;
    }

    /**
     * Method processes all resolvers stored in the processing context in batches (size is specified as parameter in generate method)
     * @param context processing context
     * @return number of processed resolvers
     */
    boolean processResolvers(final ProcessingContext context) {
        logger.log(Level.FINE, "Entering processResolvers...");
        final long start = System.currentTimeMillis();
        final List<ResolverConfiguration> allResolvers = new ArrayList<>(context.getResolverConfigurations());

        final int loops = (allResolvers.size() + this.batchSize - 1) / this.batchSize;
        final int countOfProcessedResolvers = IntStream.range(0, loops)
                .mapToObj(i -> allResolvers.subList(i * this.batchSize, Math.min(allResolvers.size(), (i + 1) * this.batchSize)))
                .map(batch -> processResolversBatch(batch, context))
                .reduce(0, Integer::sum);

        final boolean hasBeenAllResolversProcessed = allResolvers.size() == countOfProcessedResolvers;
        logger.log(Level.FINE, "Resolvers have been processed " + (hasBeenAllResolversProcessed ? "successfully" : "unsuccessfully") + " in " + (System.currentTimeMillis() - start) + " ms.");

        return hasBeenAllResolversProcessed;
    }

    /**
     * Method processes batch of resolvers
     * @param resolverConfigurations resolvers to be processed
     * @param context processing context
     * @return number of processed resolvers
     */
    int processResolversBatch(final List<ResolverConfiguration> resolverConfigurations, final ProcessingContext context) {

        // use parallel stream - more threads handle the processing
        return resolverConfigurations.parallelStream().map(resolverConfiguration -> {
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
                            resolverCacheExportTask.export(resolverConfiguration, data);
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
