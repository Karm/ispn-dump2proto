package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.PolicyRecord;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

public class PolicyRecordMarshaller implements MessageMarshaller<PolicyRecord> {
    @Override
    public Class<? extends PolicyRecord> getJavaClass() {
        return PolicyRecord.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.PolicyRecord";
    }

    @Override
    public PolicyRecord readFrom(ProtoStreamReader reader) {
        throw new UnsupportedOperationException("Read operation is not supported");
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PolicyRecord record) throws IOException {
        writer.writeInt("policyId", record.getPolicyId());
        writer.writeInt("strategy", new StrategyTypeMarshaller().marshall(record.getStrategyType()));
        writer.writeInt("audit", record.getAudit());
        writer.writeInt("block", record.getBlock());
    }
}
