package biz.karms.protostream.ioc.auxpojo;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Michal Karm Babacek
 */
@Setter
@Getter
public class TypeIocID {
    private String type;
    private String iocID;

    public TypeIocID(String type, String iocID) {
        this.type = type;
        this.iocID = iocID;
    }
}
