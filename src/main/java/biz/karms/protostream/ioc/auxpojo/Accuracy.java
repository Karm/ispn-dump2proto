package biz.karms.protostream.ioc.auxpojo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Michal Karm Babacek
 */
@Getter
@Setter
public class Accuracy {
    private String feed;
    private List<NameNumber> nameNumbers;

    public Accuracy(String feed, List<NameNumber> nameNumbers) {
        this.feed = feed;
        this.nameNumbers = nameNumbers;
    }
}
