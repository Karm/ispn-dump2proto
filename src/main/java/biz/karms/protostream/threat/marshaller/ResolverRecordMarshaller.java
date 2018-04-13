package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.CustomListRecord;
import biz.karms.protostream.threat.domain.IpRangesRecord;
import biz.karms.protostream.threat.domain.PolicyRecord;
import biz.karms.protostream.threat.domain.ResolverRecord;
import biz.karms.protostream.threat.domain.Threat;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

public class ResolverRecordMarshaller implements MessageMarshaller<ResolverRecord> {

    @Override
    public Class<? extends ResolverRecord> getJavaClass() {
        return ResolverRecord.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.ResolverRecord";
    }


    @Override
    public ResolverRecord readFrom(ProtoStreamReader reader) throws IOException {
        throw new UnsupportedOperationException("Read operation is not supported");
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ResolverRecord resolverRecord) throws IOException {
        writer.writeCollection("customLists", resolverRecord.getCustomListRecords(), CustomListRecord.class);
        writer.writeCollection("threats", resolverRecord.getThreats(), Threat.class);
        writer.writeCollection("ipRanges", resolverRecord.getIpRangesRecords(), IpRangesRecord.class);
        writer.writeCollection("policies", resolverRecord.getPolicyRecords(), PolicyRecord.class);
    }


}
