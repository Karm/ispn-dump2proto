package biz.karms.protostream.threat.marshaller;

import biz.karms.protostream.BigIntegerNormalizer;
import biz.karms.protostream.threat.domain.Flag;
import biz.karms.protostream.threat.domain.Threat;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

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
        writer.writeBytes("crc64", BigIntegerNormalizer.unsignedBigEndian(record.getCrc64().toByteArray(), 8));
        writer.writeInt("accuracy", record.getAccuracy());
        // final Byte[] flags = record.getSlots().stream().map(flag -> flag != null ? flag.getByteValue() : Flag.none.getByteValue()).toArray(Byte[]::new);
        final Flag[] flagList = record.getSlots();
        final byte[] flags = new byte[flagList.length];
        for (int i = 0; i < flagList.length; i++) {
            flags[i] = (flagList[i] != null) ? flagList[i].getByteValue() : Flag.none.getByteValue();
        }
        writer.writeBytes("flags", flags);
    }
}
