package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.threat.domain.Threat;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ThreatMarshaller implements MessageMarshaller<Threat> {
    @Override
    public Class<? extends Threat> getJavaClass() {
        return Threat.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.Threat";
    }

    @Override
    public Threat readFrom(ProtoStreamReader reader) throws IOException {
        throw new UnsupportedOperationException("Read operation is not supported");
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Threat record) throws IOException {
        writer.writeString("crc64", record.getCrc64().toString());
        writer.writeInt("accuracy", record.getAccuracy());

        final List<Integer> flags = record.getSlots().stream().map(flag -> flag != null ? (int) flag.getByteValue() : 0).collect(Collectors.toList());
        writer.writeCollection("flags", flags, Integer.class);
    }
}
