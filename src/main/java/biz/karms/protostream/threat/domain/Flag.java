package biz.karms.protostream.threat.domain;

/**
 Enumeration represents threat slot flag
 */
public enum Flag {

    /**
     * 00000000
     */
    none(0),

    /**
     * 00000001
     */
    accuracy(1),

    /**
     * 00000010
     */
    blacklist(2),

    /**
     * 00000100
     */
    whitelist(4),

    /**
     * 00001000
     */
    drop(8),

    /**
     * 00010000
     */
    audit(16);

    private int value;

    Flag(int value) {
        this.value = value;
    }

    public byte getByteValue() {
        return (byte) this.value;
    }

    public int getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return name() + "(" + this.value + ")";
    }

    public static Flag parse(int value) {
        if (accuracy.value == value)
            return accuracy;
        else if (none.value == value)
            return none;
        else if (blacklist.value == value)
            return blacklist;
        else if (whitelist.value == value)
            return whitelist;
        else if (drop.value == value)
            return drop;
        else if (audit.value == value)
            return audit;
        else {
            throw new IllegalArgumentException(String.format("The value '%s' cannot be mapped into Flag enum", value));
        }
    }
}
