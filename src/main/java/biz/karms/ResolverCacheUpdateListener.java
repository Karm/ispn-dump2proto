package biz.karms;

import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryModified;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.event.ClientCacheEntryCreatedEvent;
import org.infinispan.client.hotrod.event.ClientCacheEntryModifiedEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Michal Karm Babacek
 */
@ClientListener
public class ResolverCacheUpdateListener {

    protected static final Logger log = Logger.getLogger(ResolverCacheUpdateListener.class.getName());

    //final ConcurrentLinkedDeque<Integer> resolverIDs = new ConcurrentLinkedDeque<>();

    @ClientCacheEntryCreated
    public void handleCreatedEvent(ClientCacheEntryCreatedEvent e) {
        log.log(Level.INFO, "Resolver created: " + e.getKey());
    }

    @ClientCacheEntryModified
    public void handleModifiedEvent(ClientCacheEntryModifiedEvent e) {
        log.log(Level.INFO, "Resolver modified: " + e.getKey());
    }

}
