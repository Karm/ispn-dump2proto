package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.CustomListRecord;
import biz.karms.protostream.threat.domain.IpRangesRecord;
import biz.karms.protostream.threat.domain.PolicyRecord;
import biz.karms.protostream.threat.domain.ResolverRecord;
import biz.karms.protostream.threat.domain.Threat;
import java.util.Collections;
import java.util.List;
import org.infinispan.protostream.MessageMarshaller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

public class ResolverRecordMarshallerTest {


    private ResolverRecordMarshaller marshaller;

    @Mock
    private MessageMarshaller.ProtoStreamWriter writer;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        marshaller = new ResolverRecordMarshaller();
    }

    @Test
    public void getJavaClass() throws Exception {
        assertThat(marshaller.getJavaClass(), is(ResolverRecord.class));
    }

    @Test
    public void getTypeName() throws Exception {
        assertThat(marshaller.getTypeName(), is("sinkitprotobuf.ResolverRecord"));
    }

    @Test
    public void writeTo() throws Exception {
        // preparation
        final ResolverRecord record = new ResolverRecord();
        record.setResolverId(123);

        final List<CustomListRecord> customLists = Collections.singletonList(Mockito.mock(CustomListRecord.class));
        record.setCustomListRecords(customLists);

        final List<IpRangesRecord> ipranges = Collections.singletonList(Mockito.mock(IpRangesRecord.class));
        record.setIpRangesRecords(ipranges);

        final List<PolicyRecord> policies = Collections.singletonList(Mockito.mock(PolicyRecord.class));
        record.setPolicyRecords(policies);

        final List<Threat> threats = Collections.singletonList(Mockito.mock(Threat.class));
        record.setThreats(threats);

        // calling tested method
        marshaller.writeTo(writer, record);

        // verification
        verify(writer).writeCollection("customLists", customLists, CustomListRecord.class);
        verify(writer).writeCollection("threats", threats, Threat.class);
        verify(writer).writeCollection("ipRanges", ipranges, IpRangesRecord.class);
        verify(writer).writeCollection("policies", policies, PolicyRecord.class);
    }

}