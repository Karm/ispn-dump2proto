package biz.karms.protostream.threat.processing;

import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.resolver.EndUserConfiguration;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@Getter
public class ProcessingContext {
    private Collection<ResolverConfiguration> resolverConfigurations;
    private Collection<BlacklistedRecord> blacklistedRecords;
    private Collection<EndUserConfiguration> endUserRecords;

    public ProcessingContext() {
        this.resolverConfigurations = Collections.emptyList();
        this.blacklistedRecords = Collections.emptyList();
        this.endUserRecords = Collections.emptyList();
    }

    public void setResolverConfigurations(Collection<ResolverConfiguration> resolverConfigurations) {
        this.resolverConfigurations = Collections.unmodifiableCollection(Objects.requireNonNull(resolverConfigurations,
                "Resolver configurations cannot be null"));
    }

    public void setBlacklistedRecords(Collection<BlacklistedRecord> blacklistedRecords) {
        this.blacklistedRecords = Collections.unmodifiableCollection(Objects.requireNonNull(blacklistedRecords,
                "Blacklisted records cannot be null"));
    }

    public void setEndUserRecords(Collection<EndUserConfiguration> endUserRecords) {
        this.endUserRecords = endUserRecords;
    }
}
