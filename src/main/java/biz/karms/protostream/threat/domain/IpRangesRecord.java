package biz.karms.protostream.threat.domain;

import biz.karms.sinkit.ejb.util.CIDRUtils;
import lombok.Getter;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.Serializable;
import java.math.BigInteger;
import java.net.UnknownHostException;

@Getter
public class IpRangesRecord implements Serializable {
    private final String cidrAddress;

    private final BigInteger startIpRangeNum;

    private final BigInteger endIpRangeNum;

    private final int policyId;

    private final String identity;

    public IpRangesRecord(String cidrAddress, int policyId, String identity) throws UnknownHostException {
        this.cidrAddress = cidrAddress;
        this.policyId = policyId;
        final ImmutablePair<BigInteger, BigInteger> rangesNum = CIDRUtils.getStartEndAddressesBigInt(cidrAddress);
        this.startIpRangeNum = rangesNum.getLeft();
        this.endIpRangeNum = rangesNum.getRight();
        this.identity = identity;
    }
}
