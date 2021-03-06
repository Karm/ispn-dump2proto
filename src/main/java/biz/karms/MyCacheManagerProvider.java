package biz.karms;

import biz.karms.cache.annotations.SinkitCacheName;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.ejb.cache.pojo.WhitelistedRecord;
import biz.karms.sinkit.ejb.cache.pojo.marshallers.*;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.exceptions.TransportException;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;

import java.io.*;
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
    private static final String RESOLVER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE = "/sinkitprotobuf/resolver_configuration.proto";
    private static final String END_USER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE = "/sinkitprotobuf/end_user_configuration.proto";

    private Logger log = Logger.getLogger(MyCacheManagerProvider.class.getName());

    private RemoteCacheManager cacheManagerForIndexableCaches;
    private RemoteCacheManager cacheManager;

    public MyCacheManagerProvider(final String hotrodHost, final int hotrodPort, final long hotrodTimeout) throws IOException, InterruptedException {
        this.hotrodHost = hotrodHost;
        this.hotrodPort = hotrodPort;
        this.hotrodTimeout = hotrodTimeout;

        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Constructing caches...");

        org.infinispan.client.hotrod.configuration.ConfigurationBuilder builder = new org.infinispan.client.hotrod.configuration.ConfigurationBuilder();
        builder.tcpKeepAlive(true)
                .tcpNoDelay(true)
                .connectionPool()
                .addServer()
                .host(hotrodHost)
                .port(hotrodPort)
                .marshaller(new ProtoStreamMarshaller());
        cacheManagerForIndexableCaches = new RemoteCacheManager(builder.build());


        builder = new org.infinispan.client.hotrod.configuration.ConfigurationBuilder();
        builder.tcpKeepAlive(true)
                .tcpNoDelay(true)
                .connectionPool()
                .numTestsPerEvictionRun(3)
                .testOnBorrow(false)
                .testOnReturn(false)
                .testWhileIdle(true)
                .addServer()
                .host(hotrodHost)
                .port(hotrodPort);
        cacheManager = new RemoteCacheManager(builder.build());

        final SerializationContext ctx = ProtoStreamMarshaller.getSerializationContext(cacheManagerForIndexableCaches);
        ctx.registerProtoFiles(FileDescriptorSource.fromResources(RULE_PROTOBUF_DEFINITION_RESOURCE));
        ctx.registerMarshaller(new RuleMarshaller());
        ctx.registerMarshaller(new ImmutablePairMarshaller());
        ctx.registerProtoFiles(FileDescriptorSource.fromResources(CUSTOM_LIST_PROTOBUF_DEFINITION_RESOURCE));
        ctx.registerMarshaller(new CustomListMarshaller());
        ctx.registerProtoFiles(FileDescriptorSource.fromResources(RESOLVER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE));
        ctx.registerMarshaller(new ResolverConfigurationMessageMarshaller());
        ctx.registerMarshaller(new PolicyMessageMarshaller());
        ctx.registerMarshaller(new StrategyMessageMarshaller());
        ctx.registerMarshaller(new StrategyParamsMessageMarshaller());
        ctx.registerMarshaller(new PolicyCustomListsMarshaller());
        ctx.registerProtoFiles(FileDescriptorSource.fromResources(END_USER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE));
        ctx.registerMarshaller(new EndUserConfigurationMessageMarshaller());

        long timestamp = System.currentTimeMillis();
        while (!setupMetadataCache() && System.currentTimeMillis() - timestamp < hotrodTimeout) {
            log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": XXX");
            Thread.sleep(1000L);
            log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Waiting for the Hot Rod server on " + hotrodHost + ":" + hotrodPort + " to come up until " +
                    (System.currentTimeMillis() - timestamp) + " < " + hotrodTimeout);
        }

        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Managers started.");
    }

    private boolean setupMetadataCache() throws IOException {
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": setupMetadataCache");
        try {
            final RemoteCache<String, String> metadataCache = cacheManagerForIndexableCaches.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
            metadataCache.put(RULE_PROTOBUF_DEFINITION_RESOURCE, readResource(RULE_PROTOBUF_DEFINITION_RESOURCE));
            metadataCache.put(CUSTOM_LIST_PROTOBUF_DEFINITION_RESOURCE, readResource(CUSTOM_LIST_PROTOBUF_DEFINITION_RESOURCE));
            metadataCache.put(RESOLVER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE, this.readResource(RESOLVER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE));
            metadataCache.put(END_USER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE, this.readResource(END_USER_CONFIGURATION_PROTOBUF_DEFINITION_RESOURCE));
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

    public RemoteCacheManager getCacheManager() {
        if (cacheManager == null) {
            throw new IllegalArgumentException("cacheManager must not be null, check init");
        }
        return cacheManager;
    }

    public RemoteCache<String, BlacklistedRecord> getBlacklistCache() {
        if (cacheManager == null) {
            throw new IllegalArgumentException("Manager must not be null.");
        }
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": getBlacklistCache called.");
        return cacheManager.getCache(SinkitCacheName.infinispan_blacklist.toString());
    }

    public RemoteCache<String, WhitelistedRecord> getWhitelistCache() {
        if (cacheManager == null) {
            throw new IllegalArgumentException("Manager must not be null.");
        }
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": getWhitelistCache called.");
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

