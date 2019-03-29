package biz.karms;

import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryModified;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Michal Karm Babacek
 */
@ClientListener
public class ResolverCacheUpdateListener {

    private static final Logger log = Logger.getLogger(ResolverCacheUpdateListener.class.getName());

    private final ConcurrentLinkedDeque<Integer> resolverIDs;

    public ResolverCacheUpdateListener(ConcurrentLinkedDeque<Integer> resolverIDs) {
        this.resolverIDs = resolverIDs;
    }

    @ClientCacheEntryCreated
    public void handleCreatedEvent(ClientCacheEntryCreatedEvent e) {
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Resolver created: " + e.getKey());
        resolverIDs.push((Integer) e.getKey());
    }

    @ClientCacheEntryModified
    public void handleModifiedEvent(ClientCacheEntryModifiedEvent e) {
        log.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Resolver modified: " + e.getKey());
        resolverIDs.push((Integer) e.getKey());
    }

}
