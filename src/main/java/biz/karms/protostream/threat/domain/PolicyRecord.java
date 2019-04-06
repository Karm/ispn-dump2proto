package biz.karms.protostream.threat.domain;

import biz.karms.sinkit.resolver.StrategyType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class PolicyRecord implements Serializable {

    private int policyId;
    private StrategyType strategyType;
    private int audit;
    private int block;
}
