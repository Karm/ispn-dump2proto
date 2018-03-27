package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.domain.CustomListRecord;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.resolver.EndUserConfiguration;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class UserCustomListTaskTest {


    private UserCustomListTask userCustomListTask;

    @Before
    public void setUp() {
        final ResolverConfiguration resolverConfiguration = new ResolverConfiguration();
        resolverConfiguration.setClientId(1);

        final ProcessingContext processingContext = new ProcessingContext();

        final EndUserConfiguration conf1 = new EndUserConfiguration();
        conf1.setPolicyId(2);
        conf1.setClientId(1);
        conf1.setIdentities(new LinkedHashSet<>(Arrays.asList("user1Comp", "user1Mobile")));
        conf1.setWhitelist(new LinkedHashSet<>(Arrays.asList("whalebone.com", "google.com")));
        conf1.setBlacklist(new LinkedHashSet<>(Collections.emptyList()));

        final EndUserConfiguration conf2 = new EndUserConfiguration();
        conf2.setPolicyId(3);
        conf2.setClientId(2);
        conf2.setIdentities(new LinkedHashSet<>(Arrays.asList("anotherFax", "anotherNotebook")));
        conf2.setWhitelist(new LinkedHashSet<>(Arrays.asList("whalebone.com", "google.com")));
        conf2.setBlacklist(new LinkedHashSet<>(Collections.singletonList("blackportal.com")));


        final EndUserConfiguration conf3 = new EndUserConfiguration();
        conf3.setPolicyId(1);
        conf3.setClientId(1);
        conf3.setIdentities(new LinkedHashSet<>(Collections.singletonList("user123Computer")));
        conf3.setWhitelist(new LinkedHashSet<>(Collections.singletonList("whalebone.com")));
        conf3.setBlacklist(new LinkedHashSet<>(Collections.singletonList("blackportal.com")));


        final Collection<EndUserConfiguration> endUserRecords = new ArrayList<>();
        endUserRecords.add(conf1);
        endUserRecords.add(conf2);
        endUserRecords.add(conf3);
        processingContext.setEndUserRecords(endUserRecords);

        userCustomListTask = new UserCustomListTask(resolverConfiguration, processingContext);
    }

    @Test
    public void testProcessData() {
        final List<CustomListRecord> records = userCustomListTask.processData();
        assertThat(records, notNullValue());
        assertThat(records, hasSize(3));
        assertThat(records.get(0).getIdentity(), is("user1Comp"));
        assertThat(records.get(0).getBlacklist(), hasSize(0));
        assertThat(records.get(0).getWhitelist(), hasSize(2));
        assertThat(records.get(0).getWhitelist(), hasItems("whalebone.com", "google.com"));
        assertThat(records.get(0).getPolicyId(), is(2));

        assertThat(records.get(1).getIdentity(), is("user1Mobile"));
        assertThat(records.get(1).getBlacklist(), hasSize(0));
        assertThat(records.get(1).getWhitelist(), hasSize(2));
        assertThat(records.get(1).getWhitelist(), hasItems("whalebone.com", "google.com"));
        assertThat(records.get(1).getPolicyId(), is(2));

        assertThat(records.get(2).getIdentity(), is("user123Computer"));
        assertThat(records.get(2).getBlacklist(), hasSize(1));
        assertThat(records.get(2).getBlacklist(), hasItems("blackportal.com"));
        assertThat(records.get(2).getWhitelist(), hasSize(1));
        assertThat(records.get(2).getWhitelist(), hasItems("whalebone.com"));
        assertThat(records.get(2).getPolicyId(), is(1));

    }
}