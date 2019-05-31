package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.BigIntegerNormalizer;
import biz.karms.protostream.threat.domain.Flag;
import biz.karms.protostream.threat.domain.Threat;
import org.infinispan.protostream.MessageMarshaller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

public class ThreatMarshallerTest {

    private ThreatMarshaller marshaller;

    @Mock
    private MessageMarshaller.ProtoStreamWriter writer;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        marshaller = new ThreatMarshaller();
    }

    @Test
    public void getJavaClass() throws Exception {
        assertThat(marshaller.getJavaClass(), is(Threat.class));
    }

    @Test
    public void getTypeName() throws Exception {
        assertThat(marshaller.getTypeName(), is("sinkitprotobuf.Threat"));
    }

    @Test
    public void writeTo() throws Exception {

        // preparation
        final Threat record = new Threat(new BigInteger("14378846635097004878"));
        record.setAccuracy(50);
        record.setSlot(0, Flag.blacklist);
        record.setSlot(1, Flag.whitelist);
        record.setSlot(2, Flag.audit);
        record.setSlot(3, Flag.drop);
        record.setSlot(4, Flag.accuracy);

        final List<Flag> tmpFlags = Arrays.asList(
                Flag.blacklist,
                Flag.whitelist,
                Flag.audit,
                Flag.drop,
                Flag.accuracy,
                Flag.none, Flag.none, Flag.none, Flag.none, Flag.none, Flag.none, Flag.none
        );

        // calling tested method
        marshaller.writeTo(writer, record);

        // verification
        verify(writer).writeBytes("crc64", BigIntegerNormalizer.unsignedBigEndian(record.getCrc64().toByteArray(), 8));
        verify(writer).writeInt("accuracy", 50);

        final byte[] flags = new byte[tmpFlags.size()];
        for (int i = 0; i < tmpFlags.size(); i++) {
            flags[i] = (tmpFlags.get(i) != null) ? tmpFlags.get(i).getByteValue() : Flag.none.getByteValue();
        }
        verify(writer).writeBytes("flags", flags);
    }
}
