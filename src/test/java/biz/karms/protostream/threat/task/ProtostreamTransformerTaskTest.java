package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.domain.CustomListRecord;
import biz.karms.protostream.threat.domain.Flag;
import biz.karms.protostream.threat.domain.IpRangesRecord;
import biz.karms.protostream.threat.domain.PolicyRecord;
import biz.karms.protostream.threat.domain.ResolverRecord;
import biz.karms.protostream.threat.domain.Threat;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import biz.karms.sinkit.resolver.StrategyType;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertThat;

public class ProtostreamTransformerTaskTest {

    private ProtostreamTransformerTask transformerTask;

    private ResolverRecord resolverRecord;

    @Before
    public void setUp() throws UnknownHostException {
        transformerTask = new ProtostreamTransformerTask(Mockito.mock(ResolverConfiguration.class));

        final Threat threat = new Threat( "2315137971279604471");
        threat.setSlot0(Flag.blacklist);
        threat.setSlot2(Flag.whitelist);
        final List<Threat> threats = Arrays.asList(threat);


        final IpRangesRecord ipRangesRecord = new IpRangesRecord("10.130.10.40", 2);
        final List<IpRangesRecord> ipRanges = Arrays.asList(ipRangesRecord);

        final PolicyRecord policyRecord = new PolicyRecord(4, StrategyType.blacklist, 13, 20);
        final List<PolicyRecord> policies = Arrays.asList(policyRecord);

        final CustomListRecord customListRecord = new CustomListRecord();
        customListRecord.setId("aaa:bbb");
        customListRecord.setBlacklist(Collections.singleton("gggogle.com"));
        customListRecord.setWhitelist(Collections.singleton("whalebone.com"));
        customListRecord.setIdentity("use123Comp");
        customListRecord.setPolicyId(3);

        final List<CustomListRecord> customLists = Arrays.asList(customListRecord);

        resolverRecord = new ResolverRecord();
        resolverRecord.setResolverId(12);
        resolverRecord.setThreats(threats);
        resolverRecord.setPolicyRecords(policies);
        resolverRecord.setIpRangesRecords(ipRanges);
        resolverRecord.setCustomListRecords(customLists);
    }


    @Test
    public void testTransformToByteBuffer() {
        final ByteBuffer buffer = transformerTask.transformToProtobuf(this.resolverRecord);
        assertThat(buffer, Matchers.notNullValue());
    }

}