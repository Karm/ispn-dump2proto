package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.domain.IpRangesRecord;
import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.resolver.ResolverConfiguration;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Task responsible for creating IpRangesRecords from ResolverConfiguration
 */
public class ResolverConfigurationIpRangesTask {

    private final ResolverConfiguration resolverConfiguration;
    private final ProcessingContext context;
    private static final Logger logger = Logger.getLogger(ResolverConfigurationIpRangesTask.class.getName());

    public ResolverConfigurationIpRangesTask(ResolverConfiguration resolverConfiguration, ProcessingContext context) {
        this.resolverConfiguration = Objects.requireNonNull(resolverConfiguration, "resolvers configuration cannot null");
        this.context = Objects.requireNonNull(context, "processing context cannot be null");
    }

    /**
     * Method transforms ResolverConfiguration into required IpRangesRecords
     *
     * @return list of ip ranges records
     */
    public List<IpRangesRecord> processData() {
        logger.log(Level.INFO, "Entering processData...");
        final long start = System.currentTimeMillis();
        final List<IpRangesRecord> ipRangesRecords = this.resolverConfiguration.getPolicies().stream()
                .map(currentPolicy -> Optional.ofNullable(currentPolicy.getIpRanges()).map(Collection::stream).orElse(Stream.empty()).map(ipRange -> {
                    try {
                        return new IpRangesRecord(ipRange, currentPolicy.getId());
                    } catch (UnknownHostException e) {
                        throw new ResolverProcessingException(e, resolverConfiguration, ResolverProcessingTask.IP_RANGES_TASK);
                    }
                }).collect(Collectors.toList()))
                .collect(ArrayList::new, List::addAll, List::addAll);

        ipRangesRecords.sort(new IpRangesComparator());
        logger.log(Level.INFO, "processData finished in " + (System.currentTimeMillis() - start) + " ms.");
        return ipRangesRecords;
    }

    /**
     * Comparator used by sorting algorithm which keeps ipV4 in the top and sorted by their mask, then
     * ipV6 and sorted by their mask
     */
    static class IpRangesComparator implements Comparator<IpRangesRecord> {
        @Override
        public int compare(IpRangesRecord r1, IpRangesRecord r2) {
            String addr1 = r1.getCidrAddress();
            String addr2 = r2.getCidrAddress();

            final boolean ip4Addr1 = !addr1.contains(":");
            final boolean ip4Addr2 = !addr2.contains(":");

            addr1 = addr1 + (ip4Addr1 ? "/32" : "/128");
            addr2 = addr2 + (ip4Addr2 ? "/32" : "/128");

            final String[] parts1 = addr1.split("/");
            final String[] parts2 = addr2.split("/");

            // bigger mask number comes first
            final int mask1 = -1 * Integer.valueOf(parts1[1]);
            final int mask2 = -1 * Integer.valueOf(parts2[1]);

            //ipv4 comes first
            final int ip4VersionWeight1 = ip4Addr1 ? -1000 : 1000;
            final int ip4VersionWeight2 = ip4Addr2 ? -1000 : 1000;

            return Integer.compare((mask1 + ip4VersionWeight1), (mask2 + ip4VersionWeight2));
        }
    }
}
