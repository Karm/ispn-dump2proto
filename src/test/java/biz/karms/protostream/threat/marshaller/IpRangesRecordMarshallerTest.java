package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.IpRangesRecord;
import biz.karms.sinkit.ejb.util.CIDRUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.infinispan.protostream.MessageMarshaller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

public class IpRangesRecordMarshallerTest {
    private IpRangesRecordMarshaller marshaller;

    @Mock
    private MessageMarshaller.ProtoStreamWriter writer;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        marshaller = new IpRangesRecordMarshaller();
    }

    @Test
    public void getJavaClass() throws Exception {
        assertThat(marshaller.getJavaClass(), is(IpRangesRecord.class));
    }

    @Test
    public void getTypeName() throws Exception {
        assertThat(marshaller.getTypeName(), is("sinkitprotobuf.IpRangesRecord"));
    }

    @Test
    public void writeTo() throws Exception {

        // preparation
        final IpRangesRecord record = new IpRangesRecord("10.20.30.40/8", 1);
        final Pair<String, String> tmpRanges = CIDRUtils.getStartEndAddresses("10.20.30.40/8");

        // calling tested method
        marshaller.writeTo(writer, record);


        // verification
        verify(writer).writeString("startIpRange", tmpRanges.getLeft());
        verify(writer).writeString("endIpRange", tmpRanges.getRight());
        verify(writer).writeString("identity", null);
        verify(writer).writeInt("policyId", 1);
    }
}
