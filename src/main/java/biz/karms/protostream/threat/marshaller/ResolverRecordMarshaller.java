package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.*;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

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
    public ResolverRecord readFrom(ProtoStreamReader reader) {
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
