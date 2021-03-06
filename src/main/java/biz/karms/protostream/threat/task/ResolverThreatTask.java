package biz.karms.protostream.threat.task;

import biz.karms.crc64java.CRC64;
import biz.karms.protostream.threat.domain.Flag;
import biz.karms.protostream.threat.domain.Threat;
import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.ioc.IoCClassificationType;
import biz.karms.sinkit.resolver.Policy;
import biz.karms.sinkit.resolver.PolicyCustomList;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import biz.karms.sinkit.resolver.StrategyType;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Task responsible for creating threats list from {@link BlacklistedRecord}s which match to resolver configuration of the resolver which is being processed
 */
public class ResolverThreatTask {

    private static final Logger logger = Logger.getLogger(ResolverThreatTask.class.getName());

    private final ResolverConfiguration resolverConfiguration;
    private final ProcessingContext context;

    public ResolverThreatTask(ResolverConfiguration resolverConfiguration, ProcessingContext context) {
        this.resolverConfiguration = Objects.requireNonNull(resolverConfiguration, "resolvers configuration cannot null");
        this.context = Objects.requireNonNull(context, "processing context cannot be null");
    }


    /**
     * Method creates Threats from {@link BlacklistedRecord}s which match to ResolverConfiguration
     *
     * @return map keeps threats entities (entry = key is BlacklistedRecord.blackListedDomainOrIP, value is Threat)
     */
    public Map<BigInteger, Threat> processData() {
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Entering processData...");
        final long start = System.currentTimeMillis();
        // Given the millions of records, parallelStream is faster, but it has higher memory footprint. .stream() is used intentionally.
        final Callable<Map<BigInteger, Threat>> processing = () -> context.getBlacklistedRecords().stream()
                .map(record -> {
                    logger.log(Level.FINEST, "Starting processing of blacklisted record '{}' for resolver '#{}'",
                            new Object[]{record, this.resolverConfiguration.getResolverId()});

                    final Threat threat = new Threat(record.getCrc64Hash());
                    threat.setAccuracy(computeMaxAccuracy(record));

                    for (int policyIdx = 0; policyIdx < this.resolverConfiguration.getPolicies().size(); policyIdx++) {
                        final Policy currentPolicy = this.resolverConfiguration.getPolicies().get(policyIdx);
                        addFlagToThreatSlot(threat, record, policyIdx, currentPolicy);
                    }

                    logger.log(Level.FINEST, "Processing of blacklisted record '{}' for resolver '#{}' finished",
                            new Object[]{record, this.resolverConfiguration.getResolverId()});

                    return threat;
                })
                .filter(Threat::isSet)
                .collect(Collectors.toMap(Threat::getCrc64, Function.identity()));

        final int threadsCount = Integer.parseInt(System.getProperty("D2P_RESOLVER_THREAT_TASK_RECORD_BATCH_SIZE_S", "1"));

        final ForkJoinPool threadPool = new ForkJoinPool(threadsCount);
        try {
            logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": processData finished in " + (System.currentTimeMillis() - start) + " ms.");
            return threadPool.submit(processing).get();
        } catch (Exception e) {
            throw new ResolverProcessingException(e, resolverConfiguration, ResolverProcessingTask.THREAT_TASK);
        } finally {
            threadPool.shutdown();
        }
    }

    /**
     * Method post processes the data and modifies them according to the resolveConfiguration.customList settings
     *
     * @param resolverThreatData threats related to resolver which is being processed by this task
     * @return final list of threats
     */
    public List<Threat> postProcessData(Map<BigInteger, Threat> resolverThreatData) {
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Entering postProcessData...");
        final long start = System.currentTimeMillis();
        for (int policyIdx = 0; policyIdx < this.resolverConfiguration.getPolicies().size(); policyIdx++) {
            final Policy currentPolicy = this.resolverConfiguration.getPolicies().get(policyIdx);
            final int slotIdx = policyIdx;

            // handling audit post-processing
            Optional.of(currentPolicy).map(Policy::getCustomlists).map(PolicyCustomList::getAuditList).ifPresent(
                    audits -> handleCustomLists(audits, Flag.audit, slotIdx, () -> resolverThreatData)
            );

            // handling blacklist post-processing
            Optional.of(currentPolicy).map(Policy::getCustomlists).map(PolicyCustomList::getBlackList).ifPresent(
                    blacklists -> handleCustomLists(blacklists, Flag.blacklist, slotIdx, () -> resolverThreatData)
            );

            // handling drop post-processing
            Optional.of(currentPolicy).map(Policy::getCustomlists).map(PolicyCustomList::getDropList).ifPresent(
                    droplists -> handleCustomLists(droplists, Flag.drop, slotIdx, () -> resolverThreatData)
            );

            // handling whitelist post-processing
            Optional.of(currentPolicy).map(Policy::getCustomlists).map(PolicyCustomList::getWhiteList).ifPresent(
                    whitelists -> handleCustomLists(whitelists, Flag.whitelist, slotIdx, () -> resolverThreatData)
            );
        }

        // TODO: This is a duplicate work with UserCustomListTask: How do we leverage the work and NOT break parallelism?
        final Set<String> domainFromCustomLists = new HashSet<>();
        context.getEndUserRecords().stream()
                .filter(conf -> this.resolverConfiguration.getClientId().equals(conf.getClientId()))
                .forEach(c -> {
                    domainFromCustomLists.addAll(c.getBlacklist());
                    // We don't want to hit each smtp.seznam.cz etc. on the first lookup. White custom lists will be used later
                    // if the domain is already considered for blocking.
                    // domainFromCustomLists.addAll(c.getWhitelist());
                });
        domainFromCustomLists.forEach(domain -> {
            final BigInteger crc64 = getCrc64(domain);

            resolverThreatData.computeIfAbsent(crc64, v -> {
                final Threat threat = new Threat(crc64);
                threat.setAccuracy(0);
                threat.setTmpDomain(domain);
                IntStream.range(0, 12).forEach(i -> threat.setSlot(i, Flag.none));
                return threat;
            });
        });

        // Sort the final output
        final List<Threat> values = new ArrayList<>(resolverThreatData.values().stream().sorted(Comparator.comparing(Threat::getCrc64)).collect(Collectors.toList()));

        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": postProcessData finished in " + (System.currentTimeMillis() - start) + " ms.");
        return values;
    }

    /**
     * Method which add Flag into threat's slot
     *
     * @param threat  the threat to be updated
     * @param slotIdx slot idx to be updated
     * @param policy  policy holds configuration
     */
    void addFlagToThreatSlot(Threat threat, BlacklistedRecord record, int slotIdx, Policy policy) {
        final StrategyType strategyType = policy.getStrategy().getStrategyType();
        // if strategy is accuracy, then must match ioc type and accuracy feed AND must be in accuracy range (bigger or equal to strategy audit threshold
        if (strategyType != StrategyType.accuracy || shouldBeSetAccuracySlot(threat, record, policy)) {
            threat.setSlot(slotIdx, Flag.valueOf(strategyType.name()));
        }

        // if record's feed match to blacklist - update slot
        if (matchBlacklistedRecordByBlacklistFeed(record, policy)) {
            threat.setSlot(slotIdx, Flag.blacklist);
        }
    }

    /**
     * Method checks if the threat(accuracy)'s slot should be updated or not
     *
     * @param threat
     * @param record
     * @param policy
     * @return true if threat(accuracy)'s should be upd
     */
    boolean shouldBeSetAccuracySlot(Threat threat, BlacklistedRecord record, Policy policy) {
        return matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy) && isThreatInAccuraccyRange(threat, policy);
    }


    /**
     * Methods checks if the given record matches according to the type and accuracy feed rules
     *
     * @param record record to be tested
     * @param policy policy from which is obtained configuration
     * @return true / false
     */
    boolean matchBlacklistedRecordByTypeAndAccuracyFeed(BlacklistedRecord record, Policy policy) {
        return record.getSources().entrySet().stream()
                // check if blacklistedRecord.source.type is present in the policy.strategy.params.types
                .filter(sourceEntry -> {
                    final Set<IoCClassificationType> ioCClassificationTypes = policy.getStrategy().getStrategyParams().getTypes();
                    return ioCClassificationTypes == null || ioCClassificationTypes.isEmpty() || ioCClassificationTypes.stream()
                            .anyMatch(iocType -> iocType.getLabel().equals(sourceEntry.getValue().getLeft()));
                })
                // if yes, check if feed is present in the policy.accuracy_feeds
                .anyMatch(sourceEntry -> {
                    final Set<String> feeds = policy.getAccuracyFeeds();
                    return feeds == null || feeds.isEmpty() || feeds.stream().anyMatch(feed -> feed.equals(sourceEntry.getKey()));
                });
    }

    /**
     * Method checks if the given threat's accuracy value is at least bigger or equal to strategy audit
     *
     * @param threat threat to be checked
     * @param policy policy from which is obtained configuration
     * @return true / false
     */
    boolean isThreatInAccuraccyRange(Threat threat, Policy policy) {
        return threat.getAccuracy() >= policy.getStrategy().getStrategyParams().getAudit();
    }

    /**
     * Method checks if the given record matches to the blacklists rules
     *
     * @param record record to be tested
     * @param policy policy from which is obtained configuration
     * @return true / false
     */
    boolean matchBlacklistedRecordByBlacklistFeed(BlacklistedRecord record, Policy policy) {
        return record.getSources().entrySet().stream()
                // check if feed is present in the policy.blacklisted_feeds
                .anyMatch(sourceEntry -> {
                    final Set<String> feeds = policy.getBlacklistedFeeds();
                    return Optional.ofNullable(feeds).map(Collection::stream).orElse(Stream.empty())
                            .anyMatch(feed -> feed.equals(sourceEntry.getKey()));
                });
    }


    /**
     * Gets max sum of accuracy's items
     *
     * @return max sum
     */
    Integer computeMaxAccuracy(BlacklistedRecord record) {
        final HashMap<String, HashMap<String, Integer>> accuracyConf = record.getAccuracy();
        if (accuracyConf == null || accuracyConf.isEmpty())
            return 0;

        return record.getAccuracy().values().stream()
                .map(entry -> entry.values().stream().reduce(0, Integer::sum))
                .reduce(Math::max).orElse(0);
    }

    void handleCustomLists(final Set<String> customLists, final Flag flag, final int slotIdx, final Supplier<Map<BigInteger, Threat>> threatsSupplier) {
        Optional.of(customLists).ifPresent(data -> data.forEach(domain -> {
            final BigInteger crc64 = getCrc64(domain);
            final Threat threat = threatsSupplier.get().computeIfAbsent(crc64, Threat::new);
            threat.setTmpDomain(domain);
            threat.setSlot(slotIdx, flag);
        }));
    }

    BigInteger getCrc64(String domain) {
        return CRC64.getInstance().crc64BigInteger(domain.getBytes());
    }
}
