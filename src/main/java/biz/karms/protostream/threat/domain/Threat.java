package biz.karms.protostream.threat.domain;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
public class Threat implements Serializable {
    @Setter(AccessLevel.NONE)
    private BigInteger crc64;

    private String tmpDomain;

    private int accuracy;
    private Flag slot0;
    private Flag slot1;
    private Flag slot2;
    private Flag slot3;
    private Flag slot4;
    private Flag slot5;
    private Flag slot6;
    private Flag slot7;
    private Flag slot8;
    private Flag slot9;
    private Flag slot10;
    private Flag slot11;

    public Threat(BigInteger crc64) {
        this.crc64 = crc64;
    }

    public void setSlot(int position, Flag flag) {
        Objects.requireNonNull(flag, "Fag cannot be null");
        switch (position) {
            case 0:
                this.slot0 = flag;
                break;
            case 1:
                this.slot1 = flag;
                break;
            case 2:
                this.slot2 = flag;
                break;
            case 3:
                this.slot3 = flag;
                break;
            case 4:
                this.slot4 = flag;
                break;
            case 5:
                this.slot5 = flag;
                break;
            case 6:
                this.slot6 = flag;
                break;
            case 7:
                this.slot7 = flag;
                break;
            case 8:
                this.slot8 = flag;
                break;
            case 9:
                this.slot9 = flag;
                break;
            case 10:
                this.slot10 = flag;
                break;
            case 11:
                this.slot11 = flag;
                break;


        }
    }

    public List<Flag> getSlots() {
        return Arrays.asList(
                slot0, slot1, slot2, slot3,
                slot4, slot5, slot6, slot7,
                slot8, slot9, slot10, slot11
        );
    }

    /**
     * Method returning flag whether this threat is set or empty
     *
     * @return true if any slot is set otherwise false
     */
    public boolean isSet() {
        return getSlots().stream().anyMatch(Objects::nonNull);
    }


}
