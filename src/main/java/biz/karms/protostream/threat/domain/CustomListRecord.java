package biz.karms.protostream.threat.domain;

import biz.karms.sinkit.resolver.EndUserConfiguration;
import biz.karms.sinkit.resolver.Policy;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Set;

@Getter
@Setter
public class CustomListRecord implements Serializable {

    private String id;
    private String identity;
    private Set<String> whitelist;
    private Set<String> blacklist;
    /**
     * While ipRanges for @{@link Policy} MUST NOT be empty and are enforced in @{@link biz.karms.sinkit.ejb.util.ResolverConfigurationValidator},
     * ipRanges in {@link EndUserConfiguration} MAY be empty.
     */
    private Set<String> ipRanges;
    private int policyId;
}
