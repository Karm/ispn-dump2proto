package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.IpRangesRecord;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

public class IpRangesRecordMarshaller implements MessageMarshaller<IpRangesRecord> {
    @Override
    public Class<? extends IpRangesRecord> getJavaClass() {
        return IpRangesRecord.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.IpRangesRecord";
    }

    @Override
    public IpRangesRecord readFrom(ProtoStreamReader reader) throws IOException {
        throw new UnsupportedOperationException("Read operation is not supported");
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, IpRangesRecord record) throws IOException {
        writer.writeBytes("startIpRange", record.getStartIpRange().toByteArray());
        writer.writeBytes("endIpRange", record.getEndIpRange().toByteArray());
        writer.writeString("identity", null);
        writer.writeInt("policyId", record.getPolicyId());
    }
}

