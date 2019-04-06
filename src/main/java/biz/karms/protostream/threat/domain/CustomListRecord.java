package biz.karms.protostream.threat.domain;

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
    private int policyId;
}
