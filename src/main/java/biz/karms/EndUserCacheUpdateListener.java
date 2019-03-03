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
public class EndUserCacheUpdateListener {

    private static final Logger log = Logger.getLogger(EndUserCacheUpdateListener.class.getName());

    private final ConcurrentLinkedDeque<Integer> clientIDs;

    public EndUserCacheUpdateListener(ConcurrentLinkedDeque<Integer> clientIDs) {
        this.clientIDs = clientIDs;
    }

    @ClientCacheEntryCreated
    public void handleCreatedEvent(ClientCacheEntryCreatedEvent e) {
        log.log(Level.INFO, "End user configuration created: " + e.getKey());
        clientIDs.push(Integer.parseInt(((String) e.getKey()).split(":")[0]));
    }

    @ClientCacheEntryModified
    public void handleModifiedEvent(ClientCacheEntryModifiedEvent e) {
        log.log(Level.INFO, "End user configuration modified: " + e.getKey());
        clientIDs.push(Integer.parseInt(((String) e.getKey()).split(":")[0]));
    }

}
