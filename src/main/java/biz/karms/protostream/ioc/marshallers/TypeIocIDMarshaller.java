package biz.karms.protostream.ioc.marshallers;

import biz.karms.protostream.ioc.auxpojo.TypeIocID;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

/**
 * @author Michal Karm Babacek
 */
public class TypeIocIDMarshaller implements MessageMarshaller<TypeIocID> {
    @Override
    public Class<? extends TypeIocID> getJavaClass() {
        return TypeIocID.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.BlacklistedRecordList.BlacklistedRecord.TypeIocID";
    }

    @Override
    public TypeIocID readFrom(ProtoStreamReader reader) throws IOException {
        return new TypeIocID(reader.readString("type"), reader.readString("iocID"));
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, TypeIocID record) throws IOException {
        writer.writeString("type", record.getType());
        writer.writeString("iocID", record.getIocID());
    }
}
