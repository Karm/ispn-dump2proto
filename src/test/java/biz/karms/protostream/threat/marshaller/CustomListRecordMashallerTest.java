package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.CustomListRecord;
import java.util.Collections;
import java.util.Set;
import org.infinispan.protostream.MessageMarshaller;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;

public class CustomListRecordMashallerTest {
    private CustomListRecordMashaller marshaller;

    @Mock
    private MessageMarshaller.ProtoStreamWriter writer;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        marshaller = new CustomListRecordMashaller();
    }

    @Test
    public void getJavaClass() throws Exception {
        assertThat(marshaller.getJavaClass(), is(CustomListRecord.class));
    }

    @Test
    public void getTypeName() throws Exception {
        assertThat(marshaller.getTypeName(), is("sinkitprotobuf.CustomListRecord"));
    }

    @Test
    public void writeTo() throws Exception {
        // preparation
        final CustomListRecord record = new CustomListRecord();
        record.setIdentity("user123");
        record.setPolicyId(2);
        final Set<String> blackList = Collections.singleton("blackportal.com");
        record.setBlacklist(blackList);
        final Set<String> whitelist = Collections.singleton("whiteportal.com");
        ;
        record.setWhitelist(whitelist);

        // calling tested method
        marshaller.writeTo(writer, record);

        // verification
        verify(writer).writeString("identity", "user123");
        verify(writer).writeCollection("whitelist", whitelist, String.class);
        verify(writer).writeCollection("blacklist", blackList, String.class);
        verify(writer).writeInt("policyId", 2);
    }
}