package biz.karms.protostream;

import biz.karms.cache.annotations.SinkitCacheName;
import biz.karms.cache.pojo.CustomList;
import biz.karms.protostream.marshallers.ActionMarshaller;
import biz.karms.protostream.marshallers.CoreCacheMarshaller;
import biz.karms.protostream.marshallers.SinkitCacheEntryMarshaller;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.Search;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static biz.karms.Dump2Proto.GENERATED_PROTOFILES_DIRECTORY;
import static biz.karms.Dump2Proto.D2P_CACHE_PROTOBUF;
import static biz.karms.Dump2Proto.attr;
import static biz.karms.Dump2Proto.options;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Michal Karm Babacek
 */
public class CustomlistProtostreamGenerator implements Runnable {

    private static final Logger log = Logger.getLogger(CustomlistProtostreamGenerator.class.getName());

    private final RemoteCacheManager cacheManagerForIndexableCaches;

    private static final String customListFilePath = GENERATED_PROTOFILES_DIRECTORY + "/customlist.bin";
    private static final String customListFilePathTmp = GENERATED_PROTOFILES_DIRECTORY + "/customlist.bin.tmp";
    private static final String customListFileMd5 = GENERATED_PROTOFILES_DIRECTORY + "/customlist.bin.md5";
    private static final String customListFileMd5Tmp = GENERATED_PROTOFILES_DIRECTORY + "/customlist.bin.md5.tmp";

    public CustomlistProtostreamGenerator(final RemoteCacheManager cacheManagerForIndexableCaches) {
        this.cacheManagerForIndexableCaches = cacheManagerForIndexableCaches;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        final Map<Integer, Map<String, Action>> customerIdDomainData = new HashMap<>();
        final QueryFactory qf = Search.getQueryFactory(cacheManagerForIndexableCaches.getCache(SinkitCacheName.infinispan_custom_lists.toString()));
        final Query query = qf.from(CustomList.class).build();
        final List<CustomList> result = query.list();
        result.forEach(cl -> {
            if (StringUtils.isNotEmpty(cl.getFqdn())) {
                if (!customerIdDomainData.containsKey(cl.getCustomerId())) {
                    customerIdDomainData.put(cl.getCustomerId(), new HashMap<>());
                }
                if ("B".equals(cl.getWhiteBlackLog())) {
                    customerIdDomainData.get(cl.getCustomerId()).put(DigestUtils.md5Hex(cl.getFqdn()), Action.BLACK);
                } else if ("L".equals(cl.getWhiteBlackLog())) {
                    customerIdDomainData.get(cl.getCustomerId()).put(DigestUtils.md5Hex(cl.getFqdn()), Action.LOG);
                } else {
                    customerIdDomainData.get(cl.getCustomerId()).put(DigestUtils.md5Hex(cl.getFqdn()), Action.WHITE);
                }
            }
        });
        /*
        // TODO: revisit .getBulk() with ISPN 9
        cacheManagerForIndexableCaches.getCache(SinkitCacheName.infinispan_custom_lists.toString()).withFlags(Flag.SKIP_CACHE_LOAD).getBulk().values().forEach(e -> {
            final CustomList cl = (CustomList) e;
            if (StringUtils.isNotEmpty(cl.getFqdn())) {
                if (!customerIdDomainData.containsKey(cl.getCustomerId())) {
                    customerIdDomainData.put(cl.getCustomerId(), new HashMap<>());
                }
                if ("B".equals(cl.getWhiteBlackLog())) {
                    customerIdDomainData.get(cl.getCustomerId()).put(DigestUtils.md5Hex(cl.getFqdn()), Action.BLACK);
                } else if ("W".equals(cl.getWhiteBlackLog())) {
                    customerIdDomainData.get(cl.getCustomerId()).put(DigestUtils.md5Hex(cl.getFqdn()), Action.WHITE);
                } else {
                    // We don't serialize L, i.e. "Log only"
                }
            }
        });
        */

        log.info("CustomlistProtostreamGenerator: Pulling customlist data took: " + (System.currentTimeMillis() - start) + " ms");
        start = System.currentTimeMillis();
        final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
        try {
            ctx.registerProtoFiles(FileDescriptorSource.fromResources(D2P_CACHE_PROTOBUF));
        } catch (IOException e) {
            log.severe("Not found " + D2P_CACHE_PROTOBUF + ". Cannot recover, quitting task.");
            return;
        }
        ctx.registerMarshaller(new SinkitCacheEntryMarshaller());
        ctx.registerMarshaller(new CoreCacheMarshaller());
        ctx.registerMarshaller(new ActionMarshaller());

        customerIdDomainData.entrySet().forEach(r -> {
            log.info("CustomlistProtostreamGenerator: Serializing customer ID: " + r.getKey() + " and its " + r.getValue().size() + " records.");
            final Path customListFilePathTmpP = Paths.get(customListFilePathTmp + r.getKey());
            final Path customListFilePathP = Paths.get(customListFilePath + r.getKey());
            try (final SeekableByteChannel s = Files.newByteChannel(customListFilePathTmpP, options, attr)){
                s.write(ProtobufUtil.toByteBuffer(ctx, r.getValue()));
            } catch (IOException e) {
                log.severe("CustomlistProtostreamGenerator: failed protobuffer serialization for customer id " + r.getKey());
                e.printStackTrace();
            }

            try (final FileInputStream fis = new FileInputStream(new File(customListFilePathTmp + r.getKey()))) {
                Files.write(Paths.get(customListFileMd5Tmp + r.getKey()), DigestUtils.md5Hex(fis).getBytes());
                // There is a race condition when we swap files while REST API is reading them...
                Files.move(customListFilePathTmpP, customListFilePathP, REPLACE_EXISTING);
                Files.move(Paths.get(customListFileMd5Tmp + r.getKey()), Paths.get(customListFileMd5 + r.getKey()), REPLACE_EXISTING);
            } catch (IOException e) {
                log.severe("CustomlistProtostreamGenerator: failed protofile manipulation for customer id " + r.getKey());
                e.printStackTrace();
            }
        });
        log.info("CustomlistProtostreamGenerator: Serialization of custom lists for " + customerIdDomainData.size() + " customer ids took: " + (System.currentTimeMillis() - start) + " ms.");
    }
}
