package biz.karms.protostream.ioc.marshallers;

import biz.karms.protostream.ioc.auxpojo.Accuracy;
import biz.karms.protostream.ioc.auxpojo.NameNumber;
import biz.karms.protostream.ioc.auxpojo.Source;
import biz.karms.protostream.ioc.auxpojo.TypeIocID;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

/**
 * @author Michal Karm Babacek
 */
public class BlacklistedRecordMarshaller implements MessageMarshaller<BlacklistedRecord> {
    @Override
    public Class<? extends BlacklistedRecord> getJavaClass() {
        return BlacklistedRecord.class;
    }

    @Override
    public String getTypeName() {
        return "sinkitprotobuf.BlacklistedRecordList.BlacklistedRecord";
    }

    @Override
    public BlacklistedRecord readFrom(ProtoStreamReader reader) throws IOException {
        final Calendar listed = Calendar.getInstance(TimeZone.getDefault());
        listed.setTimeInMillis(reader.readLong("listed"));

        final Map sources = reader.readCollection("sources", new ArrayList<>(), Source.class)
                .stream().collect(Collectors.toMap(Source::getFeed, s -> new ImmutablePair<>(
                        s.getTypeIocID().getType(),
                        s.getTypeIocID().getIocID())));

        final Map accuracy = reader.readCollection("accuracies", new ArrayList<>(), Accuracy.class)
                .stream().collect(Collectors.toMap(Accuracy::getFeed, a ->
                        a.getNameNumbers().stream().collect(Collectors.toMap(NameNumber::getName, NameNumber::getNumber))));

        return new BlacklistedRecord(
                reader.readString("blackListedDomainOrIP"),
                reader.readString("crc64Hash"),
                listed,
                new HashMap<String, ImmutablePair<String, String>>(sources),
                new HashMap<String, HashMap<String, Integer>>(accuracy),
                reader.readBoolean("presentOnWhiteList"));
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, BlacklistedRecord record) throws IOException {
        writer.writeString("blackListedDomainOrIP", record.getBlackListedDomainOrIP());
        writer.writeString("crc64Hash", record.getCrc64Hash());
        writer.writeLong("listed", record.getListed().getTimeInMillis());
        writer.writeBoolean("presentOnWhiteList", record.getPresentOnWhiteList());
        writer.writeCollection("sources",
                record.getSources().entrySet().stream().map(e ->
                        new Source(e.getKey(), new TypeIocID(e.getValue().getLeft(), e.getValue().getRight()))).collect(Collectors.toList()),
                Source.class);
        writer.writeCollection("accuracies",
                record.getAccuracy().entrySet().stream().map(e ->
                        new Accuracy(e.getKey(), e.getValue().entrySet().stream().map(x ->
                                new NameNumber(x.getKey(), x.getValue())).collect(Collectors.toList()))).collect(Collectors.toList()),
                Accuracy.class);
    }
}
