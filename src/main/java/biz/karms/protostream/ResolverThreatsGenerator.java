package biz.karms.protostream;

import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.protostream.threat.processing.ResolverThreatsProcessor;
import org.infinispan.client.hotrod.RemoteCacheManager;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResolverThreatsGenerator implements Runnable {

    private static final Logger logger = Logger.getLogger(ResolverThreatsGenerator.class.getName());

    private RemoteCacheManager remoteCacheManagerForIndexableCaches;
    private int batchSize;
    private final ConcurrentLinkedDeque<Integer> resolverIDs;
    private final ConcurrentLinkedDeque<Integer> clientIDs;
    private final ThreadPoolExecutor notificationExecutor;
    private final IoCKeeper ioCKeeper;

    public ResolverThreatsGenerator(RemoteCacheManager remoteCacheManagerForIndexableCaches,
                                    int batchSize, ConcurrentLinkedDeque<Integer> resolverIDs,
                                    ConcurrentLinkedDeque<Integer> clientIDs, ThreadPoolExecutor notificationExecutor, IoCKeeper ioCKeeper) {
        this.remoteCacheManagerForIndexableCaches = remoteCacheManagerForIndexableCaches;
        this.batchSize = batchSize;
        this.resolverIDs = resolverIDs;
        this.clientIDs = clientIDs;
        this.notificationExecutor = notificationExecutor;
        this.ioCKeeper = ioCKeeper;
    }

    @Override
    public void run() {
        if ((resolverIDs != null && resolverIDs.isEmpty()) && (clientIDs != null && clientIDs.isEmpty())) {
            logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": There are no recently changed resolvers, skipping...");
            return;
        }

        if (ioCKeeper.getBlacklistedRecords().isEmpty()) {
            logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": IoCKeeper holds no IoCs, probably not ready yet. Skipping this iteration.");
            return;
        }

        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Starting exporting resolvers' cache data...");
        long start = System.currentTimeMillis();
        final boolean isAllProcessed = new ResolverThreatsProcessor(
                remoteCacheManagerForIndexableCaches,
                batchSize,
                resolverIDs,
                clientIDs,
                notificationExecutor,
                ioCKeeper).process();
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Exporting of resolvers' cache data has finished " + (isAllProcessed ? "successfully" : "unsuccessfully") + " in " + (System.currentTimeMillis() - start) + " ms.");
    }
}
