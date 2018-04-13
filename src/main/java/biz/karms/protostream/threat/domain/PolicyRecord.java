package biz.karms.protostream.threat.domain;

import biz.karms.sinkit.resolver.StrategyType;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PolicyRecord implements Serializable {

    private int policyId;
    private StrategyType strategyType;
    private int audit;
    private int block;
}
