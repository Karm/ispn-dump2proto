package biz.karms.protostream;

import biz.karms.protostream.threat.processing.ResolverThreatsProcessor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.infinispan.client.hotrod.RemoteCacheManager;

public class ResolverThreatsGenerator implements Runnable {

    private static final Logger logger = Logger.getLogger(ResolverThreatsGenerator.class.getName());

    private RemoteCacheManager remoteCacheManager;
    private RemoteCacheManager remoteCacheManagerForIndexableCaches;
    private int batchSize;

    public ResolverThreatsGenerator(RemoteCacheManager remoteCacheManager, RemoteCacheManager remoteCacheManagerForIndexableCaches, int batchSize) {
        this.remoteCacheManager = remoteCacheManager;
        this.remoteCacheManagerForIndexableCaches = remoteCacheManagerForIndexableCaches;
        this.batchSize = batchSize;
    }

    @Override
    public void run() {
        logger.log(Level.FINE, "Starting exporting resolvers' cache data");
        long start = System.currentTimeMillis();
        final boolean isAllProcessed = new ResolverThreatsProcessor(remoteCacheManager, remoteCacheManagerForIndexableCaches, batchSize).process();
        logger.log(Level.FINE, "Exporting of resolvers' cache data has finished " + (isAllProcessed ? "successfully" : "unsuccessfully") + " in " + (System.currentTimeMillis() - start) + " ms.");
    }
}
