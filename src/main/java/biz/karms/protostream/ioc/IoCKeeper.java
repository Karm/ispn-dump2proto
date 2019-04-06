package biz.karms.protostream.ioc;

import biz.karms.sinkit.ejb.cache.annotations.SinkitCacheName;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Keeps millions of records about IoCs so as the logic of the
 * generator does not need to update each time it needs them.
 *
 * @author Michal Karm Babacek
 */
public class IoCKeeper implements Runnable {


    private static final Logger log = Logger.getLogger(IoCKeeper.class.getName());
    private final RemoteCacheManager remoteCacheManager;

    private static final int MAX_BULK_SIZE = 15_000;

    private volatile Collection<BlacklistedRecord> blacklistedRecords = Collections.emptyList();
    private static IoCKeeper ioCKeeper = null;

    private IoCKeeper(final RemoteCacheManager remoteCacheManager) {
        ioCKeeper = this;
        this.remoteCacheManager = remoteCacheManager;
    }

    public static IoCKeeper getIoCKeeper(final RemoteCacheManager remoteCacheManager) {
        if (ioCKeeper == null) {
            ioCKeeper = new IoCKeeper(remoteCacheManager);
        }
        return ioCKeeper;
    }

    public Collection<BlacklistedRecord> getBlacklistedRecords() {
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Thread " + Thread.currentThread().getName() + " is getting IoC collection.");
        return Collections.unmodifiableCollection(blacklistedRecords);
    }

    @Override
    public void run() {
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Fetching IoC keys...");
        final long start = System.currentTimeMillis();
        final RemoteCache<String, BlacklistedRecord> cache = remoteCacheManager.getCache(SinkitCacheName.infinispan_blacklist.name());
        final Set<String> iocKeys = new HashSet<>(cache.withFlags(Flag.SKIP_CACHE_LOAD).keySet());
        final Collection<BlacklistedRecord> blacklistedRecords = new ArrayList<>(iocKeys.size());
        final int bulks = (iocKeys.size() % MAX_BULK_SIZE == 0) ? iocKeys.size() / MAX_BULK_SIZE : iocKeys.size() / MAX_BULK_SIZE + 1;
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Fetching: There are " + iocKeys.size() + " ioc keys to get data for in " + bulks + " bulks. Ioc keys retrieval took " + (System.currentTimeMillis() - start) + " ms.");

        final long startBulks = System.currentTimeMillis();
        for (int iteration = 0; iteration < bulks; iteration++) {
            final long startBulk = System.currentTimeMillis();
            final Set<String> bulkOfKeys = iocKeys.stream().unordered().limit(MAX_BULK_SIZE).collect(Collectors.toSet());
            iocKeys.removeAll(bulkOfKeys);
            // getAll on the cache - a very expensive call
            blacklistedRecords.addAll(cache.withFlags(Flag.SKIP_CACHE_LOAD).getAll(bulkOfKeys).values());
            log.info("Thread " + Thread.currentThread().getName() + ": fetchBlacklistedRecord: Retrieved bulk " + iteration + " in " + (System.currentTimeMillis() - startBulk) + " ms.");
        }
        log.info("Thread " + Thread.currentThread().getName() + ": fetchBlacklistedRecord: All bulks retrieved in " + (System.currentTimeMillis() - startBulks) + " ms.");

        this.blacklistedRecords = blacklistedRecords;

        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Fetching finished in " + (System.currentTimeMillis() - start) + " ms.");
    }
}
