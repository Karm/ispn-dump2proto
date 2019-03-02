package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.Flag;
import biz.karms.protostream.threat.domain.Threat;
import org.infinispan.protostream.MessageMarshaller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
        record.setSlot0(Flag.blacklist);
        record.setSlot1(Flag.whitelist);
        record.setSlot2(Flag.audit);
        record.setSlot3(Flag.drop);
        record.setSlot4(Flag.accuracy);

        final List<Integer> tmpFlags = Arrays.asList(
                (int) Flag.blacklist.getByteValue(),
                (int) Flag.whitelist.getByteValue(),
                (int) Flag.audit.getByteValue(),
                (int) Flag.drop.getByteValue(),
                (int) Flag.accuracy.getByteValue(),
                0, 0, 0, 0, 0, 0, 0
        );

        // calling tested method
        marshaller.writeTo(writer, record);

        // verification
        verify(writer).writeString("crc64", record.getCrc64().toString());
        verify(writer).writeInt("accuracy", 50);

        final ArgumentCaptor<List<Integer>> flagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(writer).writeCollection(eq("flags"), flagsCaptor.capture(), eq(Integer.class));

        final List<Integer> flags = flagsCaptor.getValue();
        for (int i = 0; i < flags.size(); i++) {
            assertThat(flags.get(i), is(tmpFlags.get(i)));
        }
    }
}
