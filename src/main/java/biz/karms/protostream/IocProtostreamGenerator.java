package biz.karms.protostream;

import biz.karms.cache.annotations.SinkitCacheName;
import biz.karms.cache.pojo.BlacklistedRecord;
import biz.karms.cache.pojo.Rule;
import biz.karms.protostream.marshallers.ActionMarshaller;
import biz.karms.protostream.marshallers.CoreCacheMarshaller;
import biz.karms.protostream.marshallers.SinkitCacheEntryMarshaller;
import org.apache.commons.codec.digest.DigestUtils;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static biz.karms.Dump2Proto.GENERATED_PROTOFILES_DIRECTORY;
import static biz.karms.Dump2Proto.D2P_CACHE_PROTOBUF;
import static biz.karms.Dump2Proto.attr;
import static biz.karms.Dump2Proto.options;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Michal Karm Babacek
 */
public class IocProtostreamGenerator implements Runnable {

    private static final Logger log = Logger.getLogger(IocProtostreamGenerator.class.getName());

    private final RemoteCache<String, BlacklistedRecord> blacklistCache;

    private final RemoteCacheManager cacheManagerForIndexableCaches;

    private static final String iocListFilePath = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin";
    private static final String iocListFilePathTmp = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin.tmp";
    private static final String iocListFileMd5 = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin.md5";
    private static final String iocListFileMd5Tmp = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin.md5.tmp";

    public IocProtostreamGenerator(final RemoteCacheManager cacheManagerForIndexableCaches, final RemoteCache<String, BlacklistedRecord> blacklistCache) {
        this.blacklistCache = blacklistCache;
        this.cacheManagerForIndexableCaches = cacheManagerForIndexableCaches;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        // TODO: Well, this hurts...  We wil probably need to use retrieve(...) and operate in chunks.
        // TODO: DistributedExecutorService, DistributedTaskBuilder and DistributedTask API ?
        // https://github.com/infinispan/infinispan/pull/4975
        // final Map<String, Action> ioclist = ioclistCache.withFlags(Flag.SKIP_CACHE_LOAD).keySet().stream().collect(Collectors.toMap(Function.identity(), s -> Action.BLACK));
        final RemoteCache<String, Rule> rulesCache = cacheManagerForIndexableCaches.getCache(SinkitCacheName.infinispan_rules.toString());
        final QueryFactory qf = Search.getQueryFactory(rulesCache);
        final Query query = qf.from(Rule.class).build();
        // Hundreds of records...
        List<Rule> results = query.list();
        final Map<Integer, Set<String>> custIdFeedUidsSink = new HashMap<>();
        final Map<Integer, Set<String>> custIdFeedUidsLog = new HashMap<>();
        results.forEach(r -> {
            final HashSet<String> sinkFeeduids = new HashSet<>(r.getSources().entrySet().stream().filter(e -> "S".equals(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet()));
            final HashSet<String> logFeeduids = new HashSet<>(r.getSources().entrySet().stream().filter(e -> "L".equals(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet()));
            if (custIdFeedUidsSink.containsKey(r.getCustomerId())) {
                custIdFeedUidsSink.get(r.getCustomerId()).addAll(sinkFeeduids);
            } else {
                custIdFeedUidsSink.put(r.getCustomerId(), sinkFeeduids);
            }
            if (custIdFeedUidsLog.containsKey(r.getCustomerId())) {
                custIdFeedUidsLog.get(r.getCustomerId()).addAll(logFeeduids);
            } else {
                custIdFeedUidsLog.put(r.getCustomerId(), logFeeduids);
            }
        });
        results = null; //Not necessary
        log.info("IOCListProtostreamGenerator: Will process IoCs for " + custIdFeedUidsSink.size() + " customer Sink ids.");
        log.info("IOCListProtostreamGenerator: Will process IoCs for " + custIdFeedUidsLog.size() + " customer Log ids.");

        // final List<String> feeduids = results.stream().map(Rule::getSources).collect(Collectors.toList()).stream().map(Map::keySet).flatMap(Set::stream).collect(Collectors.toList());
        final Map<Integer, Map<String, Action>> preparedHashes = new HashMap<>();

        // TODO: bulk? Super slow and inefficient?
        // entrySet - unsupported op
        blacklistCache.withFlags(Flag.SKIP_CACHE_LOAD).keySet().forEach(key -> {
            final BlacklistedRecord b = blacklistCache.withFlags(Flag.SKIP_CACHE_LOAD).get(key);
            if (!b.isPresentOnWhiteList()) {
                b.getSources().keySet().forEach(feeduid -> {
                    custIdFeedUidsLog.entrySet().stream().filter(e -> e.getValue().contains(feeduid)).forEach(found -> {
                        if (preparedHashes.containsKey(found.getKey())) {
                            preparedHashes.get(found.getKey()).put(key, Action.LOG);
                        } else {
                            final Map<String, Action> newHashes = new HashMap<>();
                            newHashes.put(key, Action.LOG);
                            preparedHashes.put(found.getKey(), newHashes);
                        }
                    });
                    custIdFeedUidsSink.entrySet().stream().filter(e -> e.getValue().contains(feeduid)).forEach(found -> {
                        if (preparedHashes.containsKey(found.getKey())) {
                            preparedHashes.get(found.getKey()).put(key, Action.BLACK);
                        } else {
                            final Map<String, Action> newHashes = new HashMap<>();
                            newHashes.put(key, Action.BLACK);
                            preparedHashes.put(found.getKey(), newHashes);
                        }
                    });
                });
            }
        });

/*
        blacklistCache.withFlags(Flag.SKIP_CACHE_LOAD).keySet().forEach(key -> blacklistCache.withFlags(Flag.SKIP_CACHE_LOAD).get(key).getSources().keySet().forEach(feeduid -> {
            custIdFeedUidsLog.entrySet().stream().filter(e -> e.getValue().contains(feeduid)).forEach(found -> {
                if (preparedHashes.containsKey(found.getKey())) {
                    preparedHashes.get(found.getKey()).put(key, Action.LOG);
                } else {
                    final Map<String, Action> newHashes = new HashMap<>();
                    newHashes.put(key, Action.LOG);
                    preparedHashes.put(found.getKey(), newHashes);
                }
            });
            custIdFeedUidsSink.entrySet().stream().filter(e -> e.getValue().contains(feeduid)).forEach(found -> {
                if (preparedHashes.containsKey(found.getKey())) {
                    preparedHashes.get(found.getKey()).put(key, Action.BLACK);
                } else {
                    final Map<String, Action> newHashes = new HashMap<>();
                    newHashes.put(key, Action.BLACK);
                    preparedHashes.put(found.getKey(), newHashes);
                }
            });
        }));
*/
        // TODO: 8000 is a magic number. We should profile that.
        /* Keeps getting: https://gist.github.com/Karm/2bc7bee4f71027ebea993dbf38efef7b
        final CloseableIterator<Map.Entry<Object, Object>> unfilteredIterator = blacklistCache.retrieveEntries(null, null, 8000);
        unfilteredIterator.forEachRemaining(ioc -> {
            // There are usually dozens sources
            ((BlacklistedRecord) ioc.getValue()).getSources().keySet().forEach(feeduid -> {
                final Optional<Map.Entry<Integer, Set<String>>> found = custIdFeedUids.entrySet().stream().filter(e -> e.getValue().contains(feeduid)).findFirst();
                if (found != null && found.isPresent()) {
                    if (preparedHashes.containsKey(found.get().getKey())) {
                        preparedHashes.get(found.get().getKey()).put((String) ioc.getKey(), Action.BLACK);
                    } else {
                        final Map<String, Action> newHashes = new HashMap<>();
                        newHashes.put((String) ioc.getKey(), Action.BLACK);
                        preparedHashes.put(found.get().getKey(), newHashes);
                    }
                }
            });
        });
        */

        /*
        final List<String> preparedHashes = blacklistCache.getBulk().entrySet().parallelStream()
                .filter(e -> {
                    for (String feeduid : e.getValue().getSources().keySet()) {
                        if (feeduids.contains(feeduid)) {
                            return true;
                        } // else {
                            //System.out.println("feeduid: "+feeduid);
                        //}
                    }
                    return false;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
       */

        log.info("IOCListProtostreamGenerator: Pulling ioclist data took: " + (System.currentTimeMillis() - start) + " ms");
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

        final AtomicInteger workCounter = new AtomicInteger(1);
        preparedHashes.entrySet().forEach(r -> {
            log.info("IOCListProtostreamGenerator: processing Ioc data file for serialization " + workCounter.getAndIncrement() + "/" + preparedHashes.size() + ", customer id: " + r.getKey());
            final Path iocListFilePathTmpP = Paths.get(iocListFilePathTmp + r.getKey());
            final Path iocListFilePathP = Paths.get(iocListFilePath + r.getKey());
            try {
                Files.newByteChannel(iocListFilePathTmpP, options, attr).write(ProtobufUtil.toByteBuffer(ctx, r.getValue()));
            } catch (IOException e) {
                log.severe("IOCListProtostreamGenerator: failed protobuffer serialization for customer id " + r.getKey());
                e.printStackTrace();
            }
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(new File(iocListFilePathTmp + r.getKey()));
                Files.write(Paths.get(iocListFileMd5Tmp + r.getKey()), DigestUtils.md5Hex(fis).getBytes());
                // There is a race condition when we swap files while REST API is reading them...
                Files.move(iocListFilePathTmpP, iocListFilePathP, REPLACE_EXISTING);
                Files.move(Paths.get(iocListFileMd5Tmp + r.getKey()), Paths.get(iocListFileMd5 + r.getKey()), REPLACE_EXISTING);
            } catch (IOException e) {
                log.severe("IOCListProtostreamGenerator: failed protofile manipulation for customer id " + r.getKey());
                e.printStackTrace();
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        log.severe("IOCListProtostreamGenerator: Failed to close MD5 file stream.");
                    }
                }
            }
        });
        log.info("IOCListProtostreamGenerator: Serialization of ioc lists took: " + (System.currentTimeMillis() - start) + " ms.");
    }

}
