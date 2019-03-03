package biz.karms.protostream.threat.processing;

import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.protostream.threat.task.ResolverCacheExportTask;
import biz.karms.protostream.threat.task.ResolverProcessingTask;
import biz.karms.sinkit.ejb.cache.annotations.SinkitCacheName;
import biz.karms.sinkit.resolver.EndUserConfiguration;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

@SuppressWarnings("unchecked")
public class ResolverThreatsProcessorTest {

    @Mock
    private RemoteCacheManager remoteCacheManager;

    @Mock
    private RemoteCacheManager remoteCacheManagerForIndexedCaches;

    @Mock
    private Logger logger;

    @Mock
    private ResolverConfiguration resolverConfiguration;

    @Spy
    private ProcessingContext processingContext;

    private ResolverThreatsProcessor processor;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        MockitoAnnotations.initMocks(this);
        this.processor = spy(new ResolverThreatsProcessor(remoteCacheManager, remoteCacheManagerForIndexedCaches, 20, null, null));

        // replace final logger
        final Field loggerField = ResolverThreatsProcessor.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        final Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(loggerField, loggerField.getModifiers() & ~Modifier.FINAL);
        loggerField.set(null, logger);
    }

    @Test
    public void testSetResolverCacheExportTask() throws NoSuchFieldException, IllegalAccessException {
        // preparation
        final ResolverCacheExportTask<ByteBuffer> exportTask = mock(ResolverCacheExportTask.class);
        final Field resolverCacheExportTaskField = ResolverThreatsProcessor.class.getDeclaredField("resolverCacheExportTask");
        resolverCacheExportTaskField.setAccessible(true);

        // call tested method
        this.processor.setResolverCacheExportTask(exportTask);

        // verification
        assertThat(resolverCacheExportTaskField.get(this.processor), is(exportTask));
    }

    @Test
    public void testHandleRuntimeException() throws NoSuchFieldException, IllegalAccessException {
        // preparation
        final RuntimeException runtimeException = new RuntimeException("This is test runtime exception");
        final AtomicBoolean holder = new AtomicBoolean(true);

        // call tested method
        this.processor.handleException(runtimeException, holder);

        // verification
        assertThat(holder.get(), is(false));
        verify(logger).log(eq(Level.SEVERE), eq("Unable to finish exporting resolver's data, because something went wrong"), eq(runtimeException));
    }

    @Test
    public void testHandleResolverProcessingException() throws NoSuchFieldException, IllegalAccessException {
        // preparation
        doReturn(123).when(resolverConfiguration).getResolverId();

        final RuntimeException runtimeException = new RuntimeException("This is test runtime exception");
        final ResolverProcessingException processingException = new ResolverProcessingException(runtimeException, resolverConfiguration,
                ResolverProcessingTask.POLICY_TASK);
        final AtomicBoolean holder = new AtomicBoolean(true);

        // call tested method
        this.processor.handleException(processingException, holder);

        // verification
        assertThat(holder.get(), is(false));
        verify(logger)
                .log(eq(Level.SEVERE), eq("Unable to finish exporting resolver(#'123')'s data, because processed subtask 'POLICY_TASK' has failed due to"),
                        eq(runtimeException));
    }

    @Test
    public void testFetchResolverConfigurations() {
        // preparation
        final RemoteCache<Integer, ResolverConfiguration> remoteCache = mock(RemoteCache.class, Answers.RETURNS_DEEP_STUBS);
        final Set<Integer> keys = Collections.singleton(123);
        doReturn(remoteCache).when(remoteCacheManagerForIndexedCaches).getCache(SinkitCacheName.resolver_configuration.name());
        doReturn(keys).when(remoteCache).keySet();
        final Collection<ResolverConfiguration> configurations = Collections.singletonList(resolverConfiguration);
        when(remoteCache.getAll(keys).values()).thenReturn(configurations);

        // call tested method
        final ProcessingContext context = this.processor.fetchResolverConfigurations(this.processingContext);

        // verification
        assertThat(context, notNullValue());
        assertThat(context.getResolverConfigurations(), notNullValue());
        assertThat(context.getResolverConfigurations().size(), is(1));
        assertThat(context.getResolverConfigurations().iterator().next(), is(resolverConfiguration));
    }

    @Test
    public void testFetchEndUserConfigurations() {
        // preparation
        final RemoteCache<String, EndUserConfiguration> remoteCache = mock(RemoteCache.class, Answers.RETURNS_DEEP_STUBS);
        final Set<String> keys = Collections.singleton("key123");
        doReturn(remoteCache).when(remoteCacheManagerForIndexedCaches).getCache(SinkitCacheName.end_user_configuration.name());
        doReturn(keys).when(remoteCache).keySet();
        final EndUserConfiguration endUserConfiguration = mock(EndUserConfiguration.class);
        final Collection<EndUserConfiguration> configurations = Collections.singletonList(endUserConfiguration);
        when(remoteCache.getAll(keys).values()).thenReturn(configurations);

        // call tested method
        final ProcessingContext context = this.processor.fetchEndUserConfigurations(this.processingContext);

        // verification
        assertThat(context, notNullValue());
        assertThat(context.getEndUserRecords(), notNullValue());
        assertThat(context.getEndUserRecords().size(), is(1));
        assertThat(context.getEndUserRecords().iterator().next(), is(endUserConfiguration));
    }

    @Test
    @Ignore("Not implemented")
    public void testFetchBlacklistedRecord() {
        //TODO FIX        // preparation
       /* final RemoteCache<String, BlacklistedRecord> remoteCache = mock(RemoteCache.class, Answers.RETURNS_DEEP_STUBS);
        doReturn(remoteCache).when(remoteCacheManager).getCache(SinkitCacheName.infinispan_blacklist.name());


        final CloseableIterator<Map.Entry<Object, Object>> it = mock(CloseableIterator.class);
        doReturn(it).when(remoteCache).retrieveEntries(null, 1000);
        doReturn(true, false).when(it).hasNext();

        final Set<String> iocKeys = mock(HashSet.class);
        doReturn(1).when(iocKeys).size();
        doReturn(Stream.of("key")).when(iocKeys).stream();

        final Collection<BlacklistedRecord> blacklistedRecords = mock(ArrayList.class);

        final Set<String> bulkOfKeys = mock(Set.class);


        blacklistedRecords.addAll(.values());

        final BlacklistedRecord blacklistedRecord = mock(BlacklistedRecord.class);
        doReturn(CollectionU.SimpleEntry("key", blacklistedRecord)).when(remoteCache.withFlags(Flag.SKIP_CACHE_LOAD).getAll(bulkOfKeys)).values();

        // call tested method
        final ProcessingContext context = this.processor.fetchBlacklistedRecord(this.processingContext);

        // verification
        assertThat(context, notNullValue());
        assertThat(context.getBlacklistedRecords(), notNullValue());
        assertThat(context.getBlacklistedRecords().size(), is(1));
        assertThat(context.getBlacklistedRecords().iterator().next(), is(blacklistedRecord));
        */
    }

    @Test
    public void testProcessResolvers() {

        final List<ResolverConfiguration> configurations = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            configurations.add(mock(ResolverConfiguration.class));
        }

        doReturn(configurations).when(processingContext).getResolverConfigurations();
        doReturn(20).when(this.processor)
                .processResolversBatch((List<ResolverConfiguration>) argThat(hasItems(configurations.subList(0, 20).toArray(new ResolverConfiguration[0]))),
                        eq(processingContext));
        doReturn(20).when(this.processor)
                .processResolversBatch((List<ResolverConfiguration>) argThat(hasItems(configurations.subList(20, 40).toArray(new ResolverConfiguration[0]))),
                        eq(processingContext));
        doReturn(10).when(this.processor)
                .processResolversBatch((List<ResolverConfiguration>) argThat(hasItems(configurations.subList(40, 50).toArray(new ResolverConfiguration[0]))),
                        eq(processingContext));


        final boolean result = this.processor.processResolvers(processingContext);

        // verification
        assertThat(result, is(true));
        verify(this.processor, times(3)).processResolversBatch(any(), eq(processingContext));
    }

}
