package biz.karms.protostream.threat;

import biz.karms.protostream.ioc.IoCKeeper;
import biz.karms.protostream.threat.processing.ResolverThreatsProcessor;
import biz.karms.protostream.threat.task.ResolverCacheExportTask;
import biz.karms.sinkit.ejb.cache.annotations.SinkitCacheName;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.resolver.EndUserConfiguration;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.util.CloseableIterator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ResolverThreatsProcessorIntegrationTest {

    private ResolverThreatsProcessor processor;

    private ResolverConfiguration resolverConfiguration;
    private ResolverConfiguration resolverConfiguration2;

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        MockitoAnnotations.initMocks(this);

        resolverConfiguration = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
                .fromJson(new InputStreamReader(new FileInputStream("src/test/resources/resolver_conf1.json")), ResolverConfiguration.class);

        resolverConfiguration2 = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
                .fromJson(new InputStreamReader(new FileInputStream("src/test/resources/resolver_conf2.json")), ResolverConfiguration.class);

        final List<ResolverConfiguration> resolverConfigurations = Arrays.asList(resolverConfiguration, resolverConfiguration2);
        final Set<Integer> resolverConfigurationsKeySet = resolverConfigurations.stream().map(ResolverConfiguration::getResolverId).collect(Collectors.toSet());


        JsonDeserializer<Calendar> calendarJsonDeserializer = (json, typeOfT, context) -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar c = Calendar.getInstance();
            try {
                Date date = sdf.parse(json.getAsJsonPrimitive().getAsString());
                c.setTime(date);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return c;
        };
        JsonDeserializer<Pair> pairJsonDeserializer = (json, typeOfT, context) -> {
            Set<Map.Entry<String, JsonElement>> entrySet = json.getAsJsonObject().entrySet();
            List<Pair<String, String>> list = entrySet.stream().map(entry -> new ImmutablePair<>(entry.getKey(), entry.getValue().getAsString())).collect(
                    Collectors.toList());

            return !list.isEmpty() ? list.get(0) : null;
        };


        final BlacklistedRecord record = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).setDateFormat("yyyy-MM-dd")
                .registerTypeAdapter(Calendar.class, calendarJsonDeserializer)
                .registerTypeAdapter(ImmutablePair.class, pairJsonDeserializer)
                .create()
                .fromJson(new InputStreamReader(new FileInputStream("src/test/resources/blacklisted_record.json")), BlacklistedRecord.class);
        final BlacklistedRecord record2 = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).setDateFormat("yyyy-MM-dd")
                .registerTypeAdapter(Calendar.class, calendarJsonDeserializer)
                .registerTypeAdapter(ImmutablePair.class, pairJsonDeserializer)
                .create()
                .fromJson(new InputStreamReader(new FileInputStream("src/test/resources/blacklisted_record2.json")), BlacklistedRecord.class);
        final BlacklistedRecord record3 = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).setDateFormat("yyyy-MM-dd")
                .registerTypeAdapter(Calendar.class, calendarJsonDeserializer)
                .registerTypeAdapter(ImmutablePair.class, pairJsonDeserializer)
                .create()
                .fromJson(new InputStreamReader(new FileInputStream("src/test/resources/blacklisted_record3.json")), BlacklistedRecord.class);


        final EndUserConfiguration endUserConfiguration1 = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create().fromJson(new InputStreamReader(new FileInputStream("src/test/resources/end_user_custom_list1.json")), EndUserConfiguration.class);
        final EndUserConfiguration endUserConfiguration2 = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create().fromJson(new InputStreamReader(new FileInputStream("src/test/resources/end_user_custom_list2.json")), EndUserConfiguration.class);
        final EndUserConfiguration endUserConfiguration3 = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create().fromJson(new InputStreamReader(new FileInputStream("src/test/resources/end_user_custom_list3.json")), EndUserConfiguration.class);


        final List<BlacklistedRecord> blacklistedRecords = Arrays.asList(record, record2, record3);
        final Set<BigInteger> blacklistedRecordsKeySet = blacklistedRecords.stream().map(BlacklistedRecord::getCrc64Hash).collect(Collectors.toSet());

        final List<EndUserConfiguration> endUserConfigurations = Arrays.asList(endUserConfiguration1, endUserConfiguration2, endUserConfiguration3);
        final Set<String> endUserConfigurationsKeySet = endUserConfigurations.stream().map(EndUserConfiguration::getId).collect(Collectors.toSet());

        final RemoteCacheManager nonIndexingRemoteCacheManager = Mockito.mock(RemoteCacheManager.class);
        final RemoteCacheManager indexingRemoteCacheManager = Mockito.mock(RemoteCacheManager.class);

        final RemoteCache<String, BlacklistedRecord> blacklistedRecordRemoteCache = Mockito.mock(RemoteCache.class, RETURNS_DEEP_STUBS);
        final RemoteCache<String, EndUserConfiguration> endUserConfigurationRemoteCache = Mockito.mock(RemoteCache.class, RETURNS_DEEP_STUBS);
        final RemoteCache<Integer, ResolverConfiguration> resolverConfigurationRemoteCache = Mockito.mock(RemoteCache.class, RETURNS_DEEP_STUBS);


        this.processor = Mockito.spy(new ResolverThreatsProcessor(indexingRemoteCacheManager, 1, null, null, null, IoCKeeper.getIoCKeeper(nonIndexingRemoteCacheManager)));

        doReturn(blacklistedRecordRemoteCache).when(nonIndexingRemoteCacheManager).getCache(SinkitCacheName.infinispan_blacklist.name());
        final CloseableIterator<Map.Entry<Object, Object>> blackrecordsIterator = Mockito.mock(CloseableIterator.class);
        doReturn(true, true, true, false).when(blackrecordsIterator).hasNext();
        doReturn(new AbstractMap.SimpleEntry<>(record.getCrc64Hash(), record))
                .doReturn(new AbstractMap.SimpleEntry<>(record2.getCrc64Hash(), record2))
                .doReturn(new AbstractMap.SimpleEntry<>(record3.getCrc64Hash(), record3))
                .when(blackrecordsIterator).next();
        when(blacklistedRecordRemoteCache.retrieveEntries(null, 1000)).thenReturn(blackrecordsIterator);

        doReturn(endUserConfigurationRemoteCache).when(indexingRemoteCacheManager).getCache(SinkitCacheName.end_user_configuration.name());
        doReturn(endUserConfigurationsKeySet).when(endUserConfigurationRemoteCache).keySet();
        when(endUserConfigurationRemoteCache.getAll(endUserConfigurationsKeySet).values()).thenReturn(endUserConfigurations);

        doReturn(resolverConfigurationRemoteCache).when(indexingRemoteCacheManager).getCache(SinkitCacheName.resolver_configuration.name());
        doReturn(resolverConfigurationsKeySet).when(resolverConfigurationRemoteCache).keySet();
        when(resolverConfigurationRemoteCache.getAll(resolverConfigurationsKeySet).values()).thenReturn(resolverConfigurations);

    }

    @Test
    //@Ignore("due to crc64")
    public void testProcess() {

        // preparation
        final ResolverCacheExportTask<ByteBuffer> mockExporter = Mockito.mock(ResolverCacheExportTask.class);

        this.processor.setResolverCacheExportTask(mockExporter);

        // calling the tested method
        final boolean exported = this.processor.process();

        // verification
        assertThat(exported, Matchers.is(true));

        ArgumentCaptor<ByteBuffer> content1Captor = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(mockExporter).export(eq(resolverConfiguration), content1Captor.capture(), isNull());

        ArgumentCaptor<ByteBuffer> content2Captor = ArgumentCaptor.forClass(ByteBuffer.class);
        verify(mockExporter).export(eq(resolverConfiguration2), content2Captor.capture(), isNull());

        assertThat(content1Captor.getValue(), notNullValue());
        assertThat(content2Captor.getValue(), notNullValue());
        assertThat(content1Captor.getValue(), Matchers.not(Matchers.is(content2Captor.getValue())));
    }
}
