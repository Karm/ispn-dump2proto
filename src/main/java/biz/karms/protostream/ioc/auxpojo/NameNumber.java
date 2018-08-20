package biz.karms.protostream.ioc.auxpojo;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Michal Karm Babacek
 */
@Getter
@Setter
public class NameNumber {
    private String name;
    private int number;

    public NameNumber(String name, int number) {
        this.name = name;
        this.number = number;
    }
}
