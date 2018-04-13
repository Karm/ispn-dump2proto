package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.protostream.threat.marshaller.CustomListRecordMashaller;
import biz.karms.protostream.threat.marshaller.IpRangesRecordMarshaller;
import biz.karms.protostream.threat.marshaller.PolicyRecordMarshaller;
import biz.karms.protostream.threat.marshaller.ResolverRecordMarshaller;
import biz.karms.protostream.threat.marshaller.ThreatMarshaller;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;

/**
 * Task which transform {@link biz.karms.protostream.threat.domain.ResolverRecord} entity into protobuf bytes
 */
public class ProtostreamTransformerTask {

    private static final String PROTOBUF_DEFINITION_RESOURCE = "/sinkitprotobuf/resolver_record.proto";
    private final SerializationContext ctx;
    private final ResolverConfiguration resolverConfiguration;

    public ProtostreamTransformerTask(final ResolverConfiguration resolverConfiguration) {
        this.resolverConfiguration = Objects.requireNonNull(resolverConfiguration, "resolvers configuration cannot null");

        ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
        try {
            ctx.registerProtoFiles(FileDescriptorSource.fromResources(PROTOBUF_DEFINITION_RESOURCE));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        ctx.registerMarshaller(new ResolverRecordMarshaller());
        ctx.registerMarshaller(new ThreatMarshaller());
        ctx.registerMarshaller(new IpRangesRecordMarshaller());
        ctx.registerMarshaller(new PolicyRecordMarshaller());
        ctx.registerMarshaller(new CustomListRecordMashaller());
    }


    /**
     * Transform the given record into protobuf bytes
     * @param record record to be transformed
     * @param <T> type
     * @return bytes wrapped by ByteBuffer
     */
    public <T> ByteBuffer transformToProtobuf(T record) {
        try {
            ByteBuffer byteBuffer = ProtobufUtil.toByteBuffer(ctx, record);
            return byteBuffer;
        } catch (IOException e) {
            throw new ResolverProcessingException(e, resolverConfiguration, ResolverProcessingTask.PROTOBUF_SERIALIZATION);
        }
    }
}
