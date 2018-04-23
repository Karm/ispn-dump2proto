package biz.karms.protostream.ioc.marshallers;

import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Michal Karm Babacek
 */
public class BlacklistedRecordListMarshaller implements MessageMarshaller<ArrayList> {

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.BlacklistedRecordList";
    }

    @Override
    public Class<ArrayList> getJavaClass() {
        return ArrayList.class;
    }

    @Override
    public ArrayList readFrom(ProtoStreamReader reader) throws IOException {
        return reader.readCollection("blacklistedRecords", new ArrayList<>(), BlacklistedRecord.class);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ArrayList records) throws IOException {
        writer.writeCollection("blacklistedRecords", records, BlacklistedRecord.class);
    }
}
