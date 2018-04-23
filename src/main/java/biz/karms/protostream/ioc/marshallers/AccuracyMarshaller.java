package biz.karms.protostream.ioc.marshallers;

import biz.karms.protostream.ioc.auxpojo.Accuracy;
import biz.karms.protostream.ioc.auxpojo.NameNumber;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Michal Karm Babacek
 */
public class AccuracyMarshaller implements MessageMarshaller<Accuracy> {
    @Override
    public Class<? extends Accuracy> getJavaClass() {
        return Accuracy.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.BlacklistedRecordList.BlacklistedRecord.Accuracy";
    }

    @Override
    public Accuracy readFrom(MessageMarshaller.ProtoStreamReader reader) throws IOException {
        return new Accuracy(reader.readString("feed"), reader.readCollection("nameNumbers", new ArrayList<>(), NameNumber.class));
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, Accuracy record) throws IOException {
        writer.writeString("feed", record.getFeed());
        writer.writeCollection("nameNumbers", record.getNameNumbers(), NameNumber.class);
    }
}
