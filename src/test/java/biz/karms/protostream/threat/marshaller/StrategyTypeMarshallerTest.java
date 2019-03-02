package biz.karms.protostream.threat.marshaller;

import biz.karms.sinkit.resolver.StrategyType;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class StrategyTypeMarshallerTest {

    private StrategyTypeMarshaller marshaller;

    @Before
    public void setUp() {
        marshaller = new StrategyTypeMarshaller();
    }

    @Test
    public void marshall() throws Exception {
        assertThat(marshaller.marshall(StrategyType.accuracy), is(1));
        assertThat(marshaller.marshall(StrategyType.blacklist), is(2));
        assertThat(marshaller.marshall(StrategyType.whitelist), is(4));
        assertThat(marshaller.marshall(StrategyType.drop), is(8));
    }

}
