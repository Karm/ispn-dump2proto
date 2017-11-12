package biz.karms;

import biz.karms.cache.annotations.SinkitCacheName;
import biz.karms.cache.pojo.BlacklistedRecord;
import biz.karms.cache.pojo.WhitelistedRecord;
import biz.karms.cache.pojo.marshallers.CustomListMarshaller;
import biz.karms.cache.pojo.marshallers.ImmutablePairMarshaller;
import biz.karms.cache.pojo.marshallers.RuleMarshaller;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Michal Karm Babacek
 */
public class MyCacheManagerProvider {

    private final String hotrodHost;
    private final int hotrodPort;
    private final long hotrodTimeout;

    private static final String RULE_PROTOBUF_DEFINITION_RESOURCE = "/sinkitprotobuf/rule.proto";
    private static final String CUSTOM_LIST_PROTOBUF_DEFINITION_RESOURCE = "/sinkitprotobuf/customlist.proto";

    private Logger log = Logger.getLogger(MyCacheManagerProvider.class.getName());

    private RemoteCacheManager cacheManagerForIndexableCaches;
    private RemoteCacheManager cacheManager;

    public MyCacheManagerProvider(final String hotrodHost, final int hotrodPort, final long hotrodTimeout) throws IOException, InterruptedException {
        this.hotrodHost = hotrodHost;
        this.hotrodPort = hotrodPort;
        this.hotrodTimeout = hotrodTimeout;

        log.log(Level.INFO, "Constructing caches...");

        if (cacheManagerForIndexableCaches == null) {
            org.infinispan.client.hotrod.configuration.ConfigurationBuilder builder = new org.infinispan.client.hotrod.configuration.ConfigurationBuilder();
            builder.addServer()
                    .host(hotrodHost)
                    .port(hotrodPort)
                    .marshaller(new ProtoStreamMarshaller());
            cacheManagerForIndexableCaches = new RemoteCacheManager(builder.build());
        }

        if (cacheManager == null) {
            org.infinispan.client.hotrod.configuration.ConfigurationBuilder builder = new org.infinispan.client.hotrod.configuration.ConfigurationBuilder();
            builder.addServer()
                    .host(hotrodHost)
                    .port(hotrodPort);
            cacheManager = new RemoteCacheManager(builder.build());
        }

        final SerializationContext ctx = ProtoStreamMarshaller.getSerializationContext(cacheManagerForIndexableCaches);
        ctx.registerProtoFiles(FileDescriptorSource.fromResources(RULE_PROTOBUF_DEFINITION_RESOURCE));
        ctx.registerMarshaller(new RuleMarshaller());
        ctx.registerMarshaller(new ImmutablePairMarshaller());
        ctx.registerProtoFiles(FileDescriptorSource.fromResources(CUSTOM_LIST_PROTOBUF_DEFINITION_RESOURCE));
        ctx.registerMarshaller(new CustomListMarshaller());

        long timestamp = System.currentTimeMillis();
        while (!setupMetadataCache() && System.currentTimeMillis() - timestamp < hotrodTimeout) {
            log.log(Level.INFO, "XXX");
            Thread.sleep(1000L);
            log.log(Level.INFO, "Waiting for the Hot Rod server on " + hotrodHost + ":" + hotrodPort + " to come up until " +
                    (System.currentTimeMillis() - timestamp) + " < " + hotrodTimeout);
        }

        log.log(Level.INFO, "Managers started.");
    }

    private boolean setupMetadataCache() throws IOException {
        log.log(Level.INFO, "setupMetadataCache");
        try {
            final RemoteCache<String, String> metadataCache = cacheManagerForIndexableCaches.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
            metadataCache.put(RULE_PROTOBUF_DEFINITION_RESOURCE, readResource(RULE_PROTOBUF_DEFINITION_RESOURCE));
            metadataCache.put(CUSTOM_LIST_PROTOBUF_DEFINITION_RESOURCE, readResource(CUSTOM_LIST_PROTOBUF_DEFINITION_RESOURCE));
            final String errors = metadataCache.get(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX);
            if (errors != null) {
                log.log(Level.SEVERE, "Protobuffer files, either Rule or CustomLists contained errors:\n" + errors);
                return false;
            }
        } catch (TransportException ex) {
            return false;
        }
        return true;
    }

    public RemoteCacheManager getCacheManagerForIndexableCaches() {
        if (cacheManagerForIndexableCaches == null) {
            throw new IllegalArgumentException("cacheManagerForIndexableCaches must not be null, check init");
        }
        return cacheManagerForIndexableCaches;
    }

    public RemoteCache<String, BlacklistedRecord> getBlacklistCache() {
        if (cacheManager == null) {
            throw new IllegalArgumentException("Manager must not be null.");
        }
        log.log(Level.INFO, "getBlacklistCache called.");
        return cacheManager.getCache(SinkitCacheName.infinispan_blacklist.toString());
    }

    public RemoteCache<String, WhitelistedRecord> getWhitelistCache() {
        if (cacheManager == null) {
            throw new IllegalArgumentException("Manager must not be null.");
        }
        log.log(Level.INFO, "getWhitelistCache called.");
        return cacheManager.getCache(SinkitCacheName.infinispan_whitelist.toString());
    }

    public void destroy() {
        if (cacheManager != null) {
            cacheManager.stop();
        }
        if (cacheManagerForIndexableCaches != null) {
            cacheManagerForIndexableCaches.stop();
        }
    }

    private String readResource(final String resourcePath) throws IOException {
        try (final InputStream is = getClass().getResourceAsStream(resourcePath)) {
            final Reader r = new InputStreamReader(is, "UTF-8");
            final StringWriter w = new StringWriter();
            char[] buf = new char[8192];
            int len;
            while ((len = r.read(buf)) != -1) {
                w.write(buf, 0, len);
            }
            return w.toString();
        }
    }
}

