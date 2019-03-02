package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.domain.IpRangesRecord;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.resolver.Policy;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import biz.karms.utils.CIDRUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * Test class for {@link ResolverConfigurationIpRangesTask}
 */
public class ResolverConfigurationIpRangesTaskTest {

    private ResolverConfigurationIpRangesTask ipRangesTask;
    private ResolverConfiguration resoverConfiguration;

    @Before
    public void setUp() {
        this.resoverConfiguration = new ResolverConfiguration();
        this.ipRangesTask = new ResolverConfigurationIpRangesTask(this.resoverConfiguration, Mockito.mock(ProcessingContext.class));

        final Policy policy1 = new Policy();
        policy1.setId(1);
        policy1.setIpRanges(new LinkedHashSet<>(Arrays.asList("10.20.30.40/8", "20.30.40.50/15", "2001:0db8:85a3:1234:5678:8a2e:0370:0/8")));

        final Policy policy2 = new Policy();
        policy2.setId(2);
        policy2.setIpRanges(new LinkedHashSet<>(Arrays.asList("FE80::0202:B3FF:FE1E:8329/24", "10.30.30.30/32")));

        final Policy policy3 = new Policy();
        policy3.setId(3);

        final List<Policy> policies = Arrays.asList(policy1, policy2, policy3);
        this.resoverConfiguration.setPolicies(policies);
    }

    @Test
    public void testProcessData() throws UnknownHostException {
        final List<IpRangesRecord> ipRangesRecords = ipRangesTask.processData();

        assertThat(ipRangesRecords, notNullValue());
        assertThat(ipRangesRecords, hasSize(5));
        assertThat(ipRangesRecords.get(0).getCidrAddress(), is("10.30.30.30/32"));
        assertThat(ipRangesRecords.get(0).getPolicyId(), is(2));
        Pair<String, String> ranges = CIDRUtils.getStartEndAddresses("10.30.30.30/32");

        assertThat(ipRangesRecords.get(0).getStartIpRange(), is(ranges.getLeft()));
        assertThat(ipRangesRecords.get(0).getEndIpRange(), is(ranges.getRight()));


        assertThat(ipRangesRecords.get(1).getCidrAddress(), is("20.30.40.50/15"));
        assertThat(ipRangesRecords.get(1).getPolicyId(), is(1));

        assertThat(ipRangesRecords.get(2).getCidrAddress(), is("10.20.30.40/8"));
        assertThat(ipRangesRecords.get(2).getPolicyId(), is(1));

        assertThat(ipRangesRecords.get(3).getCidrAddress(), is("FE80::0202:B3FF:FE1E:8329/24"));
        assertThat(ipRangesRecords.get(3).getPolicyId(), is(2));

        assertThat(ipRangesRecords.get(4).getCidrAddress(), is("2001:0db8:85a3:1234:5678:8a2e:0370:0/8"));
        assertThat(ipRangesRecords.get(4).getPolicyId(), is(1));

    }

}
