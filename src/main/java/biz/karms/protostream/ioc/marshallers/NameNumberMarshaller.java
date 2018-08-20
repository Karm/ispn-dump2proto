package biz.karms.protostream.ioc.marshallers;

import biz.karms.protostream.ioc.auxpojo.NameNumber;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

/**
 * @author Michal Karm Babacek
 */
public class NameNumberMarshaller implements MessageMarshaller<NameNumber> {
    @Override
    public Class<? extends NameNumber> getJavaClass() {
        return NameNumber.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.BlacklistedRecordList.BlacklistedRecord.NameNumber";
    }

    @Override
    public NameNumber readFrom(ProtoStreamReader reader) throws IOException {
        return new NameNumber(reader.readString("name"), reader.readInt("number"));
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, NameNumber record) throws IOException {
        writer.writeString("name", record.getName());
        writer.writeInt("number", record.getNumber());
    }
}
