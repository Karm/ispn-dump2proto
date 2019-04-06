package biz.karms.protostream.threat.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class ResolverRecord implements Serializable {
    private int resolverId = -1;
    private List<Threat> threats = Collections.emptyList();
    private List<IpRangesRecord> ipRangesRecords = Collections.emptyList();
    private List<PolicyRecord> policyRecords = Collections.emptyList();
    private List<CustomListRecord> customListRecords = Collections.emptyList();
}
