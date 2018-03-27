package biz.karms.protostream.threat.marshaller;

import biz.karms.sinkit.resolver.StrategyType;
import java.util.HashMap;
import java.util.Map;

public class StrategyTypeMarshaller {

    private static final Map<StrategyType, Integer> mapping;

    static {
        mapping = new HashMap<>();
        mapping.put(StrategyType.accuracy, 1);
        mapping.put(StrategyType.blacklist, 2);
        mapping.put(StrategyType.whitelist, 4);
        mapping.put(StrategyType.drop, 8);
    }


    public int marshall(StrategyType strategyType) {
        return mapping.get(strategyType);
    }

}
