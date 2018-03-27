package biz.karms.protostream.threat.domain;

import java.io.Serializable;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomListRecord implements Serializable {

    private String id;
    private String identity;
    private Set<String> whitelist;
    private Set<String> blacklist;
    private int policyId;
}
