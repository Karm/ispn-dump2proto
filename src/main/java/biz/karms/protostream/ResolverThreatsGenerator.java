package biz.karms.protostream;

import biz.karms.protostream.threat.processing.ResolverThreatsProcessor;
import org.infinispan.client.hotrod.RemoteCacheManager;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResolverThreatsGenerator implements Runnable {

    private static final Logger logger = Logger.getLogger(ResolverThreatsGenerator.class.getName());

    private RemoteCacheManager remoteCacheManager;
    private RemoteCacheManager remoteCacheManagerForIndexableCaches;
    private int batchSize;
    private final ConcurrentLinkedDeque<Integer> resolverIDs;

    public ResolverThreatsGenerator(RemoteCacheManager remoteCacheManager, RemoteCacheManager remoteCacheManagerForIndexableCaches,
                                    int batchSize, ConcurrentLinkedDeque<Integer> resolverIDs) {
        this.remoteCacheManager = remoteCacheManager;
        this.remoteCacheManagerForIndexableCaches = remoteCacheManagerForIndexableCaches;
        this.batchSize = batchSize;
        this.resolverIDs = resolverIDs;
    }

    @Override
    public void run() {
        if (resolverIDs != null && resolverIDs.isEmpty()) {
            logger.log(Level.INFO, "There are no recently changed resolvers, skipping...");
            return;
        }
        logger.log(Level.INFO, "Starting exporting resolvers' cache data...");
        long start = System.currentTimeMillis();
        final boolean isAllProcessed = new ResolverThreatsProcessor(remoteCacheManager, remoteCacheManagerForIndexableCaches, batchSize, resolverIDs).process();
        logger.log(Level.INFO, "Exporting of resolvers' cache data has finished " + (isAllProcessed ? "successfully" : "unsuccessfully") + " in " + (System.currentTimeMillis() - start) + " ms.");
    }
}
