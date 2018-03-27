package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.CustomListRecord;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

public class CustomListRecordMashaller implements MessageMarshaller<CustomListRecord> {
    @Override
    public Class<? extends CustomListRecord> getJavaClass() {
        return CustomListRecord.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.CustomListRecord";
    }

    @Override
    public CustomListRecord readFrom(ProtoStreamReader reader) throws IOException {
        throw new UnsupportedOperationException("Read operation is not supported");
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, CustomListRecord record) throws IOException {
        writer.writeString("identity", record.getIdentity());
        writer.writeCollection("whitelist", record.getWhitelist(), String.class);
        writer.writeCollection("blacklist", record.getBlacklist(), String.class);
        writer.writeInt("policyId", record.getPolicyId());
    }
}
