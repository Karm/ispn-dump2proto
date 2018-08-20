package biz.karms.protostream.threat.domain;

import biz.karms.sinkit.ejb.util.CIDRUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.Serializable;
import java.math.BigInteger;
import java.net.UnknownHostException;

@Getter
public class IpRangesRecord implements Serializable {
    private final String cidrAddress;

    @Setter
    private BigInteger startIpRange;

    @Setter
    private BigInteger endIpRange;

    private final int policyId;

    public IpRangesRecord(String cidrAddress, int policyId) throws UnknownHostException {
        this.cidrAddress = cidrAddress;
        this.policyId = policyId;
        final ImmutablePair<BigInteger, BigInteger> ranges = CIDRUtils.getStartEndAddressesBigInt(cidrAddress);
        this.startIpRange = ranges.getLeft();
        this.endIpRange = ranges.getRight();
    }
}
