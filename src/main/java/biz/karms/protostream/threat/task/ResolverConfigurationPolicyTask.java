package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.domain.PolicyRecord;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.resolver.Policy;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import biz.karms.sinkit.resolver.Strategy;
import biz.karms.sinkit.resolver.StrategyParams;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Task responsible for creating PolicyRecord from ResolverConfiguration
 */
public class ResolverConfigurationPolicyTask {

    private final ResolverConfiguration resolverConfiguration;
    private final ProcessingContext context;
    private static final Logger logger = Logger.getLogger(ResolverConfigurationPolicyTask.class.getName());

    public ResolverConfigurationPolicyTask(ResolverConfiguration resolverConfiguration, ProcessingContext context) {
        this.resolverConfiguration = Objects.requireNonNull(resolverConfiguration, "resolvers configuration cannot null");
        this.context = Objects.requireNonNull(context, "processing context cannot be null");
    }

    /**
     * Method transforms ResolverConfiguration into required PolicyRecord
     *
     * @return list of policy records
     */
    public List<PolicyRecord> processData() {
        logger.log(Level.INFO, "Entering processData...");
        final long start = System.currentTimeMillis();
        final List<PolicyRecord> policyRecords = this.resolverConfiguration.getPolicies().stream().map(currentPolicy -> {
            final Optional<Integer> auditOptional = Optional.ofNullable(currentPolicy).map(Policy::getStrategy).map(Strategy::getStrategyParams)
                    .map(StrategyParams::getAudit);
            final Optional<Integer> blockOptional = Optional.ofNullable(currentPolicy).map(Policy::getStrategy).map(Strategy::getStrategyParams)
                    .map(StrategyParams::getBlock);

            final PolicyRecord policyRecord = new PolicyRecord(currentPolicy.getId(), currentPolicy.getStrategy().getStrategyType(),
                    auditOptional.orElse(0), blockOptional.orElse(0));

            return policyRecord;
        }).collect(Collectors.toList());
        logger.log(Level.INFO, "processData finished in " + (System.currentTimeMillis() - start) + " ms.");
        return policyRecords;
    }
}
