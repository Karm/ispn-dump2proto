package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.domain.PolicyRecord;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.resolver.Policy;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import biz.karms.sinkit.resolver.Strategy;
import biz.karms.sinkit.resolver.StrategyParams;
import biz.karms.sinkit.resolver.StrategyType;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ResolverConfigurationPolicyTaskTest {

    private ResolverConfigurationPolicyTask policyTask;

    @Before
    public void setUp() {
        final ResolverConfiguration resolverConfiguration = new ResolverConfiguration();
        final Policy policy1 = new Policy();
        policy1.setId(1);
        final Strategy strategy1 = new Strategy();
        strategy1.setStrategyType(StrategyType.blacklist);
        policy1.setStrategy(strategy1);

        final Policy policy2 = new Policy();
        policy2.setId(2);
        final Strategy strategy2 = new Strategy();
        final StrategyParams params = new StrategyParams();
        params.setAudit(234);
        params.setBlock(345);
        strategy2.setStrategyType(StrategyType.accuracy);
        strategy2.setStrategyParams(params);
        policy2.setStrategy(strategy2);

        final Policy policy3 = new Policy();
        policy3.setId(3);
        final Strategy strategy3 = new Strategy();
        final StrategyParams params3 = new StrategyParams();
        params3.setAudit(0);
        params3.setBlock(0);
        strategy3.setStrategyType(StrategyType.accuracy);
        strategy3.setStrategyParams(params3);
        policy3.setStrategy(strategy3);

        resolverConfiguration.setPolicies(Arrays.asList(policy1, policy2, policy3));

        policyTask = new ResolverConfigurationPolicyTask(resolverConfiguration, Mockito.mock(ProcessingContext.class));
    }

    @Test
    public void testProcessData() {
        final List<PolicyRecord> policyRecords = policyTask.processData();

        assertThat(policyRecords, notNullValue());
        assertThat(policyRecords, hasSize(3));
        assertThat(policyRecords.get(0).getPolicyId(), is(1));
        assertThat(policyRecords.get(0).getAudit(), is(0));
        assertThat(policyRecords.get(0).getBlock(), is(0));
        assertThat(policyRecords.get(0).getStrategyType(), is(StrategyType.blacklist));

        assertThat(policyRecords.get(1).getPolicyId(), is(2));
        assertThat(policyRecords.get(1).getAudit(), is(234));
        assertThat(policyRecords.get(1).getBlock(), is(345));
        assertThat(policyRecords.get(1).getStrategyType(), is(StrategyType.accuracy));

        assertThat(policyRecords.get(2).getPolicyId(), is(3));
        assertThat(policyRecords.get(2).getAudit(), is(0));
        assertThat(policyRecords.get(2).getBlock(), is(0));
        assertThat(policyRecords.get(2).getStrategyType(), is(StrategyType.accuracy));
    }

}
