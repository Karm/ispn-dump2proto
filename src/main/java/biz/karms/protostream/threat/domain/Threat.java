package biz.karms.protostream.threat.domain;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;

@Getter
@Setter
@ToString
public class Threat implements Serializable {
    @Setter(AccessLevel.NONE)
    private BigInteger crc64;

    private String tmpDomain;

    private int accuracy;
    private Flag[] slots = new Flag[12];

    public Threat(BigInteger crc64) {
        this.crc64 = crc64;
    }

    public void setSlot(int position, Flag flag) {
        Objects.requireNonNull(flag, "Fag cannot be null");
        slots[position] = flag;
    }

    public Flag[] getSlots() {
        return slots;
    }

    /**
     * Method returning flag whether this threat is set or empty
     *
     * @return true if any slot is set otherwise false
     */
    public boolean isSet() {
        for (Flag flag : slots) {
            if (flag != null) {
                return true;
            }
        }
        return false;
    }


}
