package biz.karms.protostream;

import biz.karms.cache.annotations.SinkitCacheName;
import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.protostream.marshallers.ActionMarshaller;
import biz.karms.protostream.marshallers.CoreCacheMarshaller;
import biz.karms.protostream.marshallers.SinkitCacheEntryMarshaller;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.ejb.cache.pojo.CustomList;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.infinispan.client.hotrod.Flag;
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
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static biz.karms.Dump2Proto.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Michal Karm Babacek
 */
public class IoCWithCustomProtostreamGenerator implements Runnable {

    private static final Logger log = Logger.getLogger(IoCWithCustomProtostreamGenerator.class.getName());

    private final RemoteCacheManager cacheManagerForIndexableCaches;

    private final IoCKeeper ioCKeeper;

    private final SCOPE scope;

    public enum SCOPE {
        // Expensive
        ALL,
        // Relatively cheap (loads all records from file in memory until we migrate to streaming)
        CUSTOM_LISTS_ONLY
    }

    private static final String iocWithCustomFilePath = GENERATED_PROTOFILES_DIRECTORY + "/iocWithCustom.bin";
    private static final String iocWithCustomFilePathTmp = GENERATED_PROTOFILES_DIRECTORY + "/iocWithCustom.bin.tmp";
    private static final String iocWithCustomFileMd5 = GENERATED_PROTOFILES_DIRECTORY + "/iocWithCustom.bin.md5";
    private static final String iocWithCustomFileMd5Tmp = GENERATED_PROTOFILES_DIRECTORY + "/iocWithCustom.bin.md5.tmp";

    public IoCWithCustomProtostreamGenerator(final RemoteCacheManager cacheManagerForIndexableCaches, final SCOPE scope, final IoCKeeper ioCKeeper) {
        this.cacheManagerForIndexableCaches = cacheManagerForIndexableCaches;
        this.scope = scope;
        this.ioCKeeper = ioCKeeper;
    }

    @Override
    public void run() {
        /*
        Expensive and should be less frequent, generates the whole file with all IoC from all feeds
        somebody has at last on audit level plus all custom lists domains.
         */
        if (scope == SCOPE.ALL) {

            if (ioCKeeper.getBlacklistedRecords().isEmpty()) {
                log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": IoCKeeper holds no IoCs, probably not ready yet. Skipping this iteration.");
                return;
            }

            long start = System.currentTimeMillis();
            final Map<String, Action> iocWithCustom = new HashMap<>();
            final QueryFactory qf = Search.getQueryFactory(cacheManagerForIndexableCaches.getCache(SinkitCacheName.infinispan_custom_lists.toString()).withFlags(Flag.SKIP_CACHE_LOAD));
            final Query query = qf.from(CustomList.class).build();
            final List<CustomList> result = query.list();
            result.forEach(cl -> {
                if (StringUtils.isNotEmpty(cl.getFqdn())) {
                    iocWithCustom.put(DigestUtils.md5Hex(cl.getFqdn()), Action.CHECK);
                }
            });

            iocWithCustom.putAll(ioCKeeper.getBlacklistedRecords().stream().unordered().filter(e -> !e.getPresentOnWhiteList())
                    .collect(Collectors.toMap(BlacklistedRecord::getBlackListedDomainOrIP, s -> Action.CHECK)));


            final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
            try {
                ctx.registerProtoFiles(FileDescriptorSource.fromResources(D2P_CACHE_PROTOBUF));
            } catch (IOException e) {
                log.severe("IoCWithCustom: Not found " + D2P_CACHE_PROTOBUF + ". Cannot recover, quitting task.");
                return;
            }
            ctx.registerMarshaller(new SinkitCacheEntryMarshaller());
            ctx.registerMarshaller(new CoreCacheMarshaller());
            ctx.registerMarshaller(new ActionMarshaller());
            final Path iocWithCustomFilePathTmpP = Paths.get(iocWithCustomFilePathTmp);
            final Path iocWithCustomFilePathP = Paths.get(iocWithCustomFilePath);
            try (SeekableByteChannel s = Files.newByteChannel(iocWithCustomFilePathTmpP, options, attr)) {
                s.write(ProtobufUtil.toByteBuffer(ctx, iocWithCustom));
            } catch (IOException e) {
                log.severe("IoCWithCustom: Not found " + D2P_CACHE_PROTOBUF + ". Cannot recover, quitting task.");
                return;
            }
            log.info("Thread " + Thread.currentThread().getName() + ": IoCWithCustom: Serialization to " + iocWithCustomFilePathTmp + " took: " + (System.currentTimeMillis() - start) + " ms.");
            start = System.currentTimeMillis();
            try (FileInputStream fis = new FileInputStream(new File(iocWithCustomFilePathTmp))) {
                Files.write(Paths.get(iocWithCustomFileMd5Tmp), DigestUtils.md5Hex(fis).getBytes());
                fis.close();
                // There is a race condition when we swap files while REST API is reading them...
                Files.move(iocWithCustomFilePathTmpP, iocWithCustomFilePathP, REPLACE_EXISTING);
                Files.move(Paths.get(iocWithCustomFileMd5Tmp), Paths.get(iocWithCustomFileMd5), REPLACE_EXISTING);
            } catch (IOException e) {
                log.severe("IoCWithCustom: failed protofile manipulation.");
                e.printStackTrace();
            }
            log.info("Thread " + Thread.currentThread().getName() + ": IoCWithCustom: MD5 sum and move took: " + (System.currentTimeMillis() - start) + " ms.");

        /*
        Inexpensive, loads the generated file if it exists and updates it with just custom lists data.
        if the file doesn't exist at all, it creates it and fills it with custom lists data. There are no
        IoCs present until the ALL_IOC_TAG logic runs as scheduled by D2P_ALL_IOC_PROTOSTREAM_GENERATOR_D_H_M_S.

        The drawback is that records don't get removed until ALL_IOC_TAG phase regenerates the file.
        */
        } else if (scope == SCOPE.CUSTOM_LISTS_ONLY) {

            final File iocWithCustomBinary = new File(iocWithCustomFilePath);
            final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder().build());
            try {
                ctx.registerProtoFiles(FileDescriptorSource.fromResources(D2P_CACHE_PROTOBUF));
            } catch (IOException e) {
                log.severe("IoCWithCustom: Not found " + D2P_CACHE_PROTOBUF + ". Cannot recover, quitting task.");
                return;
            }
            ctx.registerMarshaller(new SinkitCacheEntryMarshaller());
            ctx.registerMarshaller(new CoreCacheMarshaller());
            ctx.registerMarshaller(new ActionMarshaller());

            final Path iocWithCustomFilePathTmpP = Paths.get(iocWithCustomFilePathTmp);
            final Path iocWithCustomFilePathP = Paths.get(iocWithCustomFilePath);
            final Map<String, Action> iocWithCustom;

            long start = System.currentTimeMillis();

            if (iocWithCustomBinary.exists()) {
                try (InputStream is = new FileInputStream(iocWithCustomBinary)) {
                    iocWithCustom = ProtobufUtil.readFrom(ctx, is, HashMap.class);
                } catch (IOException e) {
                    e.printStackTrace();
                    log.severe("IoCWithCustom: " + iocWithCustomFilePath + " being empty / non-deserializable is unexpected. We abort.");
                    return;
                }
                log.info("Thread " + Thread.currentThread().getName() + ": IoCWithCustom: Deserialization of " + iocWithCustom.size() + " records took " + (System.currentTimeMillis() - start) + " ms.");
            } else {
                iocWithCustom = new HashMap<>();
            }

            start = System.currentTimeMillis();

            // Load custom lists from Infinispan and update what's in memory
            final QueryFactory qf = Search.getQueryFactory(cacheManagerForIndexableCaches.getCache(SinkitCacheName.infinispan_custom_lists.toString()).withFlags(Flag.SKIP_CACHE_LOAD));
            final Query query = qf.from(CustomList.class).build();
            final List<CustomList> result = query.list();
            result.forEach(cl -> {
                if (StringUtils.isNotEmpty(cl.getFqdn())) {
                    // We add, we don't remove
                    iocWithCustom.putIfAbsent(DigestUtils.md5Hex(cl.getFqdn()), Action.CHECK);
                }
            });

            try (SeekableByteChannel s = Files.newByteChannel(iocWithCustomFilePathTmpP, options, attr)) {
                // Serialize from memory to file
                s.write(ProtobufUtil.toByteBuffer(ctx, iocWithCustom));
            } catch (IOException e) {
                log.severe("IoCWithCustom: Cannot write generated file " + iocWithCustomFilePath);
                return;
            }

            try (FileInputStream fis = new FileInputStream(new File(iocWithCustomFilePathTmp))) {
                Files.write(Paths.get(iocWithCustomFileMd5Tmp), DigestUtils.md5Hex(fis).getBytes());
                fis.close();
                // There is a race condition when we swap files while REST API is reading them...
                Files.move(iocWithCustomFilePathTmpP, iocWithCustomFilePathP, REPLACE_EXISTING);
                Files.move(Paths.get(iocWithCustomFileMd5Tmp), Paths.get(iocWithCustomFileMd5), REPLACE_EXISTING);
                log.info("Thread " + Thread.currentThread().getName() + ": IoCWithCustom: Update from cache with " + result.size() + " custom list records and serialization of all " + iocWithCustom.size() + " records took " + (System.currentTimeMillis() - start) + " ms.");
            } catch (IOException e) {
                log.severe("IoCWithCustom: failed protofile manipulation.");
                e.printStackTrace();
            }
        } else {
            log.severe("IoCWithCustom: Unknown timer. Either \"" + SCOPE.ALL + "\" or \"" + SCOPE.CUSTOM_LISTS_ONLY + "\" expected. Skipping IoCWithCustom altogether.");
        }
    }
}
