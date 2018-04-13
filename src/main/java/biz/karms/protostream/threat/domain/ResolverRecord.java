package biz.karms.protostream.threat.domain;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ResolverRecord implements Serializable {
    private int resolverId = -1;
    private List<Threat> threats = Collections.emptyList();
    private List<IpRangesRecord> ipRangesRecords = Collections.emptyList();
    private List<PolicyRecord> policyRecords = Collections.emptyList();
    private List<CustomListRecord> customListRecords = Collections.emptyList();
}
