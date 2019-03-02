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
public class EndUserCacheUpdateListener {

    protected static final Logger log = Logger.getLogger(EndUserCacheUpdateListener.class.getName());

    //final ConcurrentLinkedDeque<Integer> endUserConfigIDs = new ConcurrentLinkedDeque<>();

    @ClientCacheEntryCreated
    public void handleCreatedEvent(ClientCacheEntryCreatedEvent e) {
        log.log(Level.INFO, "End user configuration created: " + e.getKey());
    }

    @ClientCacheEntryModified
    public void handleModifiedEvent(ClientCacheEntryModifiedEvent e) {
        log.log(Level.INFO, "End user configuration modified: " + e.getKey());
    }

}
