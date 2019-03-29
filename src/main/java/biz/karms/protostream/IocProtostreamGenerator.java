package biz.karms.protostream;

import biz.karms.cache.annotations.SinkitCacheName;
import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.protostream.marshallers.ActionMarshaller;
import biz.karms.protostream.marshallers.CoreCacheMarshaller;
import biz.karms.protostream.marshallers.SinkitCacheEntryMarshaller;
import biz.karms.sinkit.ejb.cache.pojo.Rule;
import org.apache.commons.codec.digest.DigestUtils;
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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static biz.karms.Dump2Proto.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.toCollection;

/**
 * @author Michal Karm Babacek
 */
public class IocProtostreamGenerator implements Runnable {

    private static final Logger log = Logger.getLogger(IocProtostreamGenerator.class.getName());

    private final IoCKeeper ioCKeeper;

    private final RemoteCacheManager cacheManagerForIndexableCaches;

    private static final String iocListFilePath = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin";
    private static final String iocListFilePathTmp = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin.tmp";
    private static final String iocListFileMd5 = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin.md5";
    private static final String iocListFileMd5Tmp = GENERATED_PROTOFILES_DIRECTORY + "/ioclist.bin.md5.tmp";

    public IocProtostreamGenerator(final RemoteCacheManager cacheManagerForIndexableCaches, final IoCKeeper ioCKeeper) {
        this.ioCKeeper = ioCKeeper;
        this.cacheManagerForIndexableCaches = cacheManagerForIndexableCaches;
    }

    @Override
    public void run() {

        if (ioCKeeper.getBlacklistedRecords().isEmpty()) {
            log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": IoCKeeper holds no IoCs, probably not ready yet. Skipping this iteration.");
            return;
        }

        long overallStart = System.currentTimeMillis();
        long start = overallStart;
        final RemoteCache<String, Rule> rulesCache = cacheManagerForIndexableCaches.getCache(SinkitCacheName.infinispan_rules.toString());
        final QueryFactory qf = Search.getQueryFactory(rulesCache);
        final Query query = qf.from(Rule.class).build();
        // Hundreds of records...
        final List<Rule> results = query.list();
        final Map<Integer, Set<String>> custIdFeedUidsSink = new HashMap<>();
        final Map<Integer, Set<String>> custIdFeedUidsLog = new HashMap<>();
        results.forEach(r -> {
            final HashSet<String> sinkFeeduids = r.getSources().entrySet().stream().filter(e -> "S".equals(e.getValue())).map(Map.Entry::getKey).collect(toCollection(HashSet::new));
            final HashSet<String> logFeeduids = r.getSources().entrySet().stream().filter(e -> "L".equals(e.getValue())).map(Map.Entry::getKey).collect(toCollection(HashSet::new));
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
        log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: Will process IoCs for " + custIdFeedUidsSink.size() + " customer Sink ids.");
        log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: Will process IoCs for " + custIdFeedUidsLog.size() + " customer Log ids.");
        log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: FeedUids lookup took " + (System.currentTimeMillis() - start) + " ms.");

        // final List<String> feeduids = results.stream().map(Rule::getSources).collect(Collectors.toList()).stream().map(Map::keySet).flatMap(Set::stream).collect(Collectors.toList());
        final Map<Integer, Map<String, Action>> preparedHashes = new HashMap<>();

        ioCKeeper.getBlacklistedRecords().stream().unordered().forEach(v -> {
            String k = v.getBlackListedDomainOrIP();
            if (!v.getPresentOnWhiteList()) {
                v.getSources().keySet().forEach(feeduid -> {
                    custIdFeedUidsLog.entrySet().stream().filter(e -> e.getValue().contains(feeduid)).forEach(found -> {
                        if (preparedHashes.containsKey(found.getKey())) {
                            preparedHashes.get(found.getKey()).put(k, Action.LOG);
                        } else {
                            final Map<String, Action> newHashes = new HashMap<>();
                            newHashes.put(k, Action.LOG);
                            preparedHashes.put(found.getKey(), newHashes);
                        }
                    });
                    custIdFeedUidsSink.entrySet().stream().filter(e -> e.getValue().contains(feeduid)).forEach(found -> {
                        if (preparedHashes.containsKey(found.getKey())) {
                            preparedHashes.get(found.getKey()).put(k, Action.BLACK);
                        } else {
                            final Map<String, Action> newHashes = new HashMap<>();
                            newHashes.put(k, Action.BLACK);
                            preparedHashes.put(found.getKey(), newHashes);
                        }
                    });
                });
            }

        });

        // Serialization fo tiles
        start = System.currentTimeMillis();
        final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
        try {
            ctx.registerProtoFiles(FileDescriptorSource.fromResources(D2P_CACHE_PROTOBUF));
        } catch (IOException e) {
            e.printStackTrace();
            log.severe("Not found " + D2P_CACHE_PROTOBUF + ". Cannot recover, quitting task.");
            return;
        }
        ctx.registerMarshaller(new SinkitCacheEntryMarshaller());
        ctx.registerMarshaller(new CoreCacheMarshaller());
        ctx.registerMarshaller(new ActionMarshaller());

        final AtomicInteger workCounter = new AtomicInteger(1);
        preparedHashes.forEach((k, v) -> {
            log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: processing Ioc data file for serialization " + workCounter.getAndIncrement() + "/" + preparedHashes.size() + ", customer id: " + k);
            Path iocListFilePathTmpP = Paths.get(iocListFilePathTmp + k);
            Path iocListFilePathP = Paths.get(iocListFilePath + k);
            try (SeekableByteChannel s = Files.newByteChannel(iocListFilePathTmpP, options, attr)) {
                s.write(ProtobufUtil.toByteBuffer(ctx, v));
                log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: " + iocListFilePathTmp + k + " written.");
            } catch (IOException e) {
                log.severe("IOCListProtostreamGenerator: failed protobuffer serialization for customer id " + k);
                e.printStackTrace();
            }
            try (FileInputStream fis = new FileInputStream(new File(iocListFilePathTmp + k))) {
                Files.write(Paths.get(iocListFileMd5Tmp + k), DigestUtils.md5Hex(fis).getBytes());
                log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: " + iocListFileMd5Tmp + k + " written.");
                // There is a race condition when we swap files while REST API is reading them...
                Files.move(iocListFilePathTmpP, iocListFilePathP, REPLACE_EXISTING);
                log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: " + iocListFilePathTmp + k + " moved to " + iocListFilePath + k + ".");
                Files.move(Paths.get(iocListFileMd5Tmp + k), Paths.get(iocListFileMd5 + k), REPLACE_EXISTING);
                log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: " + iocListFileMd5Tmp + k + " moved to " + iocListFileMd5 + k + ".");
            } catch (IOException e) {
                log.severe("IOCListProtostreamGenerator: failed protofile manipulation for customer id " + k);
                e.printStackTrace();
            }
        });
        log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: Serialization of ioc lists took: " + (System.currentTimeMillis() - start) + " ms.");
        long overallTimeSpent = (System.currentTimeMillis() - overallStart);
        log.info("Thread " + Thread.currentThread().getName() + ": IOCListProtostreamGenerator: All IoC processing took " + overallTimeSpent + " ms.");
    }
}
