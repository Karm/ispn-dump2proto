package biz.karms.protostream.ioc.auxpojo;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Michal Karm Babacek
 */
@Setter
@Getter
public class Source {
    private String feed;
    private TypeIocID typeIocID;

    public Source(String feed, TypeIocID typeIocID) {
        this.feed = feed;
        this.typeIocID = typeIocID;
    }
}
