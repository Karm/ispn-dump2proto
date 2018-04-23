package biz.karms.protostream.ioc.marshallers;

import biz.karms.protostream.ioc.auxpojo.Source;
import biz.karms.protostream.ioc.auxpojo.TypeIocID;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

/**
 * @author Michal Karm Babacek
 */
public class SourceMarshaller implements MessageMarshaller<Source> {
    @Override
    public Class<? extends Source> getJavaClass() {
        return Source.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.BlacklistedRecordList.BlacklistedRecord.Source";
    }

    @Override
    public Source readFrom(ProtoStreamReader reader) throws IOException {
        return new Source(reader.readString("feed"), reader.readObject("typeIocID", TypeIocID.class));
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Source record) throws IOException {
        writer.writeString("feed", record.getFeed());
        writer.writeObject("typeIocID", record.getTypeIocID(), TypeIocID.class);
    }
}
