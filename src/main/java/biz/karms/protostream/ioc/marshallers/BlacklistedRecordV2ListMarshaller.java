package biz.karms.protostream.ioc.marshallers;

import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecordV2;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Michal Karm Babacek
 */
public class BlacklistedRecordV2ListMarshaller implements MessageMarshaller<ArrayList> {

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.BlacklistedRecordV2List";
    }

    @Override
    public Class<ArrayList> getJavaClass() {
        return ArrayList.class;
    }

    @Override
    public ArrayList readFrom(ProtoStreamReader reader) throws IOException {
        return reader.readCollection("blacklistedRecords", new ArrayList<>(), BlacklistedRecordV2.class);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ArrayList records) throws IOException {
        writer.writeCollection("blacklistedRecords", records, BlacklistedRecordV2.class);
    }
}
