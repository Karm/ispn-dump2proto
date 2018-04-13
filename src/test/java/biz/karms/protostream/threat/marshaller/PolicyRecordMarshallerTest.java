package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.PolicyRecord;
import biz.karms.sinkit.resolver.StrategyType;
import org.infinispan.protostream.MessageMarshaller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

public class PolicyRecordMarshallerTest {

    private PolicyRecordMarshaller marshaller;

    @Mock
    private MessageMarshaller.ProtoStreamWriter writer;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        marshaller = new PolicyRecordMarshaller();
    }

    @Test
    public void getJavaClass() throws Exception {
        assertThat(marshaller.getJavaClass(), is(PolicyRecord.class));
    }

    @Test
    public void getTypeName() throws Exception {
        assertThat(marshaller.getTypeName(), is("sinkitprotobuf.PolicyRecord"));
    }

    @Test
    public void writeTo() throws Exception {
        // preparation
        final PolicyRecord policyRecord = new PolicyRecord(2, StrategyType.accuracy, 30, 50);

        // calling tested method
        marshaller.writeTo(writer, policyRecord);

        // verification
        verify(writer).writeInt("policyId", 2);
        verify(writer).writeInt("strategy", 1);
        verify(writer).writeInt("audit", 30);
        verify(writer).writeInt("block", 50);
    }

}