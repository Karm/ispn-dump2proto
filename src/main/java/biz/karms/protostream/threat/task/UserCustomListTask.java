package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.domain.CustomListRecord;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.resolver.ResolverConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Task responsible for creating CustomListRecords from EndUserConfigurations related to resolver which is being processed
 */
public class UserCustomListTask {

    private static final Logger logger = Logger.getLogger(UserCustomListTask.class.getName());

    private final ResolverConfiguration resolverConfiguration;
    private final ProcessingContext context;

    public UserCustomListTask(final ResolverConfiguration resolverConfiguration, ProcessingContext context) {
        this.resolverConfiguration = Objects.requireNonNull(resolverConfiguration, "resolvers configuration cannot null");
        this.context = Objects.requireNonNull(context, "processing context cannot be null");
    }

    /**
     * Method transforms all custom list data related to resolver
     *
     * @return list of custom list record
     */
    public List<CustomListRecord> processData() {
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": Entering export...");
        final long start = System.currentTimeMillis();
        final List<CustomListRecord> result = context.getEndUserRecords().stream()
                .filter(conf -> this.resolverConfiguration.getClientId().equals(conf.getClientId()))
                .map(endUserConfiguration -> endUserConfiguration.getIdentities().stream().map(identity -> {
                    final CustomListRecord record = new CustomListRecord();
                    record.setId(endUserConfiguration.getId());
                    record.setPolicyId(endUserConfiguration.getPolicyId());
                    record.setIdentity(identity);
                    record.setWhitelist(endUserConfiguration.getWhitelist());
                    record.setBlacklist(endUserConfiguration.getBlacklist());
                    return record;
                }).collect(Collectors.toList()))
                .collect(ArrayList::new, List::addAll, List::addAll);
        logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + ": export finished in " + (System.currentTimeMillis() - start) + " ms.");
        return result;
    }
}
