package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.protostream.threat.marshaller.*;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

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
     *
     * @param record record to be transformed
     * @param <T>    type
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

    /**
     * Source:  FasterXML/jackson-databind
     * License: http://www.apache.org/licenses/LICENSE-2.0
     */
    public static class BBufferIStream extends InputStream {
        private final ByteBuffer b;

        public BBufferIStream(ByteBuffer b) {
            this.b = b;
        }

        @Override
        public int available() {
            return b.remaining();
        }

        @Override
        public int read() throws IOException {
            return b.hasRemaining() ? (b.get() & 0xFF) : -1;
        }

        @Override
        public int read(byte[] bytes, int off, int len) throws IOException {
            if (!b.hasRemaining()) return -1;
            len = Math.min(len, b.remaining());
            b.get(bytes, off, len);
            return len;
        }
    }
}
