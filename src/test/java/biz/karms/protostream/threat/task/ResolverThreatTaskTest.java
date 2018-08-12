package biz.karms.protostream.threat.task;

import biz.karms.crc64java.CRC64;
import biz.karms.protostream.threat.domain.Flag;
import biz.karms.protostream.threat.domain.Threat;
import biz.karms.protostream.threat.processing.ProcessingContext;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.ioc.IoCClassificationType;
import biz.karms.sinkit.resolver.Policy;
import biz.karms.sinkit.resolver.PolicyCustomList;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import biz.karms.sinkit.resolver.Strategy;
import biz.karms.sinkit.resolver.StrategyParams;
import biz.karms.sinkit.resolver.StrategyType;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link ResolverThreatTask}
 */
public class ResolverThreatTaskTest {

    private ResolverThreatTask resolverThreatTask;

    private ProcessingContext context;

    @Mock
    private BlacklistedRecord mockRecord;

    @Mock
    private ResolverConfiguration mockConfiguration;

    @Mock
    private HashMap<BigInteger, Threat> mockThreats;

    @Before
    public void setUp() throws IOException, ClassNotFoundException {
        MockitoAnnotations.initMocks(this);

        context = new ProcessingContext();
        this.resolverThreatTask = Mockito.spy(new ResolverThreatTask(mockConfiguration, context));
    }

    @Test
    public void testIsThreatInAccuraccyRange() {
        // global preparation
        final Threat threat = new Threat(new BigInteger("666"));
        final Policy policy = new Policy();
        final Strategy strategy = new Strategy();
        final StrategyParams params = new StrategyParams();
        params.setAudit(15);
        params.setBlock(50);

        strategy.setStrategyParams(params);
        policy.setStrategy(strategy);

        // preparation tc1
        threat.setAccuracy(30);
        // call tested method
        boolean result = this.resolverThreatTask.isThreatInAccuraccyRange(threat, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc2
        threat.setAccuracy(15);
        // call tested method
        result = this.resolverThreatTask.isThreatInAccuraccyRange(threat, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc3
        threat.setAccuracy(14);
        // call tested method
        result = this.resolverThreatTask.isThreatInAccuraccyRange(threat, policy);
        // verification
        assertThat(result, is(false));

        // preparation tc4
        threat.setAccuracy(50);
        // call tested method
        result = this.resolverThreatTask.isThreatInAccuraccyRange(threat, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc5
        threat.setAccuracy(51);
        // call tested method
        result = this.resolverThreatTask.isThreatInAccuraccyRange(threat, policy);
        // verification
        assertThat(result, is(true));
    }

    @Test
    public void testMatchBlacklistedRecordByTypeAndAccuracyFeed() {
        // global preparation
        final BlacklistedRecord record = new BlacklistedRecord("domain", new BigInteger("666"), Calendar.getInstance(), new HashMap<>(), new HashMap<>(), false);
        record.getSources().put("phishtank", new ImmutablePair<>("phishing", "abc123"));
        record.getSources().put("mfsk", new ImmutablePair<>("content", "bcd234"));

        final Policy policy = new Policy();
        final Strategy strategy = new Strategy();
        final StrategyParams params = new StrategyParams();

        strategy.setStrategyParams(params);
        policy.setStrategy(strategy);

        final Set<String> accuracyFeeds = new HashSet<>();
        policy.setAccuracyFeeds(accuracyFeeds);

        // preparation tc1
        params.setTypes(new HashSet<>(Arrays.asList(IoCClassificationType.cc, IoCClassificationType.content, IoCClassificationType.phishing)));
        accuracyFeeds.add("phishtank");
        accuracyFeeds.add("mfcr");
        // calling tested method
        boolean result = this.resolverThreatTask.matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy);
        // verification
        assertThat(result, is(true));


        // preparation tc2
        params.setTypes(new HashSet<>(Arrays.asList(IoCClassificationType.cc, IoCClassificationType.content, IoCClassificationType.phishing)));
        accuracyFeeds.clear(); // empty means all
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc3
        policy.setAccuracyFeeds(null); // null means all
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc4
        params.setTypes(new HashSet<>(Collections.singletonList(IoCClassificationType.cc))); // record is not reported under this type
        policy.setAccuracyFeeds(accuracyFeeds);
        accuracyFeeds.add("phishtank");
        accuracyFeeds.add("mfcr");
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy);
        // verification
        assertThat(result, is(false));

        // preparation tc5
        params.setTypes(new HashSet<>(Arrays.asList(IoCClassificationType.cc, IoCClassificationType.content, IoCClassificationType.phishing)));
        accuracyFeeds.clear();
        accuracyFeeds.add("mfcr"); // record is not from this source
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy);
        // verification
        assertThat(result, is(false));

        // preparation tc6
        params.setTypes(null); // null means all
        accuracyFeeds.add("phishtank");
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc7
        params.setTypes(new HashSet<>()); //empty means all
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByTypeAndAccuracyFeed(record, policy);
        // verification
        assertThat(result, is(true));

    }

    @Test
    public void testmatchBlacklistedRecordByBlacklistFeed() {
        // global preparation
        final BlacklistedRecord record = new BlacklistedRecord("domain", new BigInteger("666"), Calendar.getInstance(), new HashMap<>(), new HashMap<>(), false);
        record.getSources().put("phishtank", new ImmutablePair<>("phishing", "abc123"));
        record.getSources().put("mfsk", new ImmutablePair<>("content", "bcd234"));

        final Policy policy = new Policy();
        final Set<String> blackFeed = new HashSet<>();
        policy.setBlacklistedFeeds(blackFeed);

        // preparation tc1
        blackFeed.add("mfsk");
        // calling tested method
        boolean result = this.resolverThreatTask.matchBlacklistedRecordByBlacklistFeed(record, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc2
        policy.setBlacklistedFeeds(null);
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByBlacklistFeed(record, policy);
        // verification
        assertThat(result, is(false));

        // preparation tc3
        policy.setBlacklistedFeeds(new HashSet<>());
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByBlacklistFeed(record, policy);
        // verification
        assertThat(result, is(false));

        // preparation tc4
        policy.setBlacklistedFeeds(blackFeed);
        blackFeed.clear();
        blackFeed.add("mfcr");
        // calling tested method
        result = this.resolverThreatTask.matchBlacklistedRecordByBlacklistFeed(record, policy);
        // verification
        assertThat(result, is(false));
    }

    @Test
    public void testComputeMaxAccuracy() {
        final BlacklistedRecord record = new BlacklistedRecord("domain", new BigInteger("666"), Calendar.getInstance(), new HashMap<>(), new HashMap<>(), false);
        final HashMap<String, Integer> map1 = new HashMap<>();
        map1.put("a", 13);
        map1.put("b", -5);
        map1.put("c", 40);
        final HashMap<String, Integer> map2 = new HashMap<>();
        map2.put("c", 7);
        map2.put("e", 16);
        map2.put("f", 4);
        record.getAccuracy().put("phishtank", map1); //48
        record.getAccuracy().put("mfsk", map2); //27

        // preparation tc1
        // calling tested method
        Integer result = this.resolverThreatTask.computeMaxAccuracy(record);
        // verification
        assertThat(result, is(48));

        // preparation tc2
        map2.put("g", -7);
        map2.put("h", 30);
        // calling tested method
        result = this.resolverThreatTask.computeMaxAccuracy(record);
        // verification
        assertThat(result, is(50));


        // preparation tc3
        record.getAccuracy().clear();
        // calling tested method
        result = this.resolverThreatTask.computeMaxAccuracy(record);
        // verification
        assertThat(result, is(0));

        // preparation tc4
        record.setAccuracy(null);
        // calling tested method
        result = this.resolverThreatTask.computeMaxAccuracy(record);
        // verification
        assertThat(result, is(0));
    }


    @Test
    public void testAccuracySlotSetting() {
        final Policy policy = Mockito.mock(Policy.class);
        final Threat threat = Mockito.mock(Threat.class);

        // preparation tc1
        doReturn(true).when(this.resolverThreatTask).matchBlacklistedRecordByTypeAndAccuracyFeed(mockRecord, policy);
        doReturn(true).when(this.resolverThreatTask).isThreatInAccuraccyRange(threat, policy);
        // calling tested method
        boolean result = this.resolverThreatTask.shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // verification
        assertThat(result, is(true));

        // preparation tc2
        doReturn(false).when(this.resolverThreatTask).isThreatInAccuraccyRange(threat, policy);
        // calling tested method
        result = this.resolverThreatTask.shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // verification
        assertThat(result, is(false));

        // preparation tc3
        doReturn(false).when(this.resolverThreatTask).matchBlacklistedRecordByTypeAndAccuracyFeed(mockRecord, policy);
        // calling tested method
        result = this.resolverThreatTask.shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // verification
        assertThat(result, is(false));

        // preparation tc4
        doReturn(true).when(this.resolverThreatTask).isThreatInAccuraccyRange(threat, policy);
        // calling tested method
        result = this.resolverThreatTask.shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // verification
        assertThat(result, is(false));
    }

    @Test
    public void testAddFlagToThreatSlot_strategyType() {
        final Policy policy = Mockito.mock(Policy.class, Mockito.RETURNS_DEEP_STUBS);
        final Threat threat = Mockito.mock(Threat.class);
        final int slotIdx = 0;

        doReturn(false).when(this.resolverThreatTask).matchBlacklistedRecordByBlacklistFeed(mockRecord, policy);

        // preparation tc1
        when(policy.getStrategy().getStrategyType()).thenReturn(StrategyType.blacklist);
        doReturn(true).when(this.resolverThreatTask).shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // calling tested method
        this.resolverThreatTask.addFlagToThreatSlot(threat, mockRecord, slotIdx, policy);
        // verification
        verify(threat).setSlot(slotIdx, Flag.blacklist);
        verify(this.resolverThreatTask, never()).shouldBeSetAccuracySlot(threat, mockRecord, policy);

        // preparation tc2
        when(policy.getStrategy().getStrategyType()).thenReturn(StrategyType.blacklist);
        doReturn(false).when(this.resolverThreatTask).shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // calling tested method
        this.resolverThreatTask.addFlagToThreatSlot(threat, mockRecord, slotIdx, policy);
        // verification
        verify(threat, times(2)).setSlot(slotIdx, Flag.blacklist);
        verify(this.resolverThreatTask, never()).shouldBeSetAccuracySlot(threat, mockRecord, policy);

        // preparation tc3
        when(policy.getStrategy().getStrategyType()).thenReturn(StrategyType.accuracy);
        doReturn(false).when(this.resolverThreatTask).shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // calling tested method
        this.resolverThreatTask.addFlagToThreatSlot(threat, mockRecord, slotIdx, policy);
        // verification
        verify(threat, never()).setSlot(slotIdx, Flag.accuracy);
        verify(this.resolverThreatTask).shouldBeSetAccuracySlot(threat, mockRecord, policy);

        // preparation tc4
        when(policy.getStrategy().getStrategyType()).thenReturn(StrategyType.accuracy);
        doReturn(true).when(this.resolverThreatTask).shouldBeSetAccuracySlot(threat, mockRecord, policy);
        // calling tested method
        this.resolverThreatTask.addFlagToThreatSlot(threat, mockRecord, slotIdx, policy);
        // verification
        verify(threat).setSlot(slotIdx, Flag.accuracy);
        verify(this.resolverThreatTask, times(2)).shouldBeSetAccuracySlot(threat, mockRecord, policy);
    }

    @Test
    public void testAddFlagToThreatSlot_blacklisted() {
        final Policy policy = Mockito.mock(Policy.class, Mockito.RETURNS_DEEP_STUBS);
        final Threat threat = Mockito.mock(Threat.class);
        final int slotIdx = 0;

        when(policy.getStrategy().getStrategyType()).thenReturn(StrategyType.accuracy);
        doReturn(false).when(this.resolverThreatTask).shouldBeSetAccuracySlot(threat, mockRecord, policy);

        // preparation tc1
        doReturn(false).when(this.resolverThreatTask).matchBlacklistedRecordByBlacklistFeed(mockRecord, policy);
        // calling tested method
        this.resolverThreatTask.addFlagToThreatSlot(threat, mockRecord, slotIdx, policy);
        // verification
        verify(threat, never()).setSlot(slotIdx, Flag.blacklist);
        verify(this.resolverThreatTask).matchBlacklistedRecordByBlacklistFeed(mockRecord, policy);

        // preparation tc1
        doReturn(true).when(this.resolverThreatTask).matchBlacklistedRecordByBlacklistFeed(mockRecord, policy);
        // calling tested method
        this.resolverThreatTask.addFlagToThreatSlot(threat, mockRecord, slotIdx, policy);
        // verification
        verify(threat).setSlot(slotIdx, Flag.blacklist);
        verify(this.resolverThreatTask, times(2)).matchBlacklistedRecordByBlacklistFeed(mockRecord, policy);
    }

    @Test
    public void testBlacklistedRecordMatched() throws Exception {
        //preparation
        this.context.setBlacklistedRecords(Collections.singleton(mockRecord));
        final int accuracyValue = 123;
        final BigInteger domainCrc64 = CRC64.getInstance().crc64BigInteger("whalebone.com".getBytes());
        doReturn(accuracyValue).when(this.resolverThreatTask).computeMaxAccuracy(mockRecord);

        final Policy policy = Mockito.mock(Policy.class, Mockito.RETURNS_DEEP_STUBS);
        doReturn(Collections.singletonList(policy)).when(mockConfiguration).getPolicies();
        doReturn(domainCrc64).when(mockRecord).getCrc64Hash();
        when(policy.getStrategy().getStrategyType()).thenReturn(StrategyType.blacklist);


        // calling tested method
        final Map<BigInteger, Threat> threats = this.resolverThreatTask.processData();

        ArgumentCaptor<Threat> threatCaptor = ArgumentCaptor.forClass(Threat.class);

        // verification
        verify(this.resolverThreatTask).addFlagToThreatSlot(threatCaptor.capture(), eq(mockRecord), eq(0), eq(policy));
        final Threat threat = threatCaptor.getValue();
        assertThat(threat, notNullValue());
        assertThat(threat.getAccuracy(), is(accuracyValue));
        assertThat(threat.getCrc64(), is(domainCrc64));
        assertThat(threat.isSet(), is(true));

        assertThat(threats, notNullValue());
        assertThat(threats, aMapWithSize(1));
        assertThat(threats, Matchers.hasEntry(threat.getCrc64(), threat));
    }

    @Test
    public void testBlacklistedRecordNotMatched() throws Exception {
        //preparation
        this.context.setBlacklistedRecords(Collections.singleton(mockRecord));

        final int accuracyValue = 234;
        final String domain = "whalebone.com";
        final BigInteger crc64Domain = CRC64.getInstance().crc64BigInteger(domain.getBytes());
        doReturn(accuracyValue).when(this.resolverThreatTask).computeMaxAccuracy(mockRecord);

        final Policy policy = Mockito.mock(Policy.class, Mockito.RETURNS_DEEP_STUBS);
        doReturn(Collections.singletonList(policy)).when(mockConfiguration).getPolicies();
        doReturn(crc64Domain).when(mockRecord).getCrc64Hash();
        when(policy.getStrategy().getStrategyType()).thenReturn(StrategyType.accuracy);

        // calling tested method
        final Map<BigInteger, Threat> threats = this.resolverThreatTask.processData();

        final ArgumentCaptor<Threat> threatCaptor = ArgumentCaptor.forClass(Threat.class);

        // verification
        verify(this.resolverThreatTask).addFlagToThreatSlot(threatCaptor.capture(), eq(mockRecord), eq(0), eq(policy));
        final Threat threat = threatCaptor.getValue();
        assertThat(threat, notNullValue());
        assertThat(threat.getAccuracy(), is(accuracyValue));
        assertThat(threat.getCrc64(), is(crc64Domain));
        assertThat(threat.isSet(), is(false));

        assertThat(threats, aMapWithSize(0));
    }

    @Test
    public void testCustomListExists() throws Exception {

        // preparing
        final String domain = "whalebone.org";
        final BigInteger crc64 = CRC64.getInstance().crc64BigInteger(domain.getBytes());
        doReturn(crc64).when(this.resolverThreatTask).getCrc64(domain);
        final Set<String> domains = Collections.singleton(domain);
        final int idx = 0;
        final Flag flag = Flag.whitelist;

        final Map<BigInteger, Threat> threatMap = new HashMap<>();
        final Threat existingThreat = new Threat(crc64);
        existingThreat.setSlot0(Flag.blacklist);
        threatMap.put(crc64, existingThreat);

        // calling tested method
        this.resolverThreatTask.handleCustomLists(domains, flag, idx, () -> threatMap);

        // verification
        assertThat(threatMap, aMapWithSize(1));
        final Threat updatedThreat = threatMap.get(crc64);
        assertThat(updatedThreat, notNullValue());
        assertThat(updatedThreat.getSlot0(), is(Flag.whitelist));
    }

    @Test
    public void testCustomListNotExists() throws Exception {
        // preparing
        final CRC64 crc64Hasher = CRC64.getInstance();
        final String domain = "whalebone.org";
        final String existingDomain = "existingDomain";
        final BigInteger crc64 = crc64Hasher.crc64BigInteger(domain.getBytes());
        final BigInteger crc64ExistingDomain = crc64Hasher.crc64BigInteger(existingDomain.getBytes());
        doReturn(crc64).when(this.resolverThreatTask).getCrc64(domain);
        doReturn(crc64ExistingDomain).when(this.resolverThreatTask).getCrc64(existingDomain);

        final Set<String> domains = Collections.singleton(domain);
        final int idx = 0;
        final Flag flag = Flag.whitelist;

        final Map<BigInteger, Threat> threatMap = new HashMap<>();
        Threat existingThreat = new Threat(crc64ExistingDomain);
        existingThreat.setSlot0(Flag.blacklist);
        threatMap.put(crc64ExistingDomain, existingThreat);

        // calling tested method
        this.resolverThreatTask.handleCustomLists(domains, flag, idx, () -> threatMap);

        // verification
        assertThat(threatMap, aMapWithSize(2));
        existingThreat = threatMap.get(crc64ExistingDomain);
        assertThat(existingThreat, notNullValue());
        assertThat(existingThreat.getSlot0(), is(Flag.blacklist));
        final Threat newThreat = threatMap.get(crc64);
        assertThat(newThreat, notNullValue());
        assertThat(newThreat.getSlot0(), is(Flag.whitelist));
    }

    @Test
    public void testPostProcessing() throws Exception {
        // preparing
        final Policy policy = new Policy();
        final PolicyCustomList customLists = new PolicyCustomList();
        final Threat threat = Mockito.mock(Threat.class);

        final Set<String> audits = Collections.singleton("audit");
        final Set<String> blackList = Collections.singleton("black");
        final Set<String> dropList = Collections.singleton("drop");
        final Set<String> whiteList = Collections.singleton("white");
        doReturn(new BigInteger("1073251900497484785")).when(this.resolverThreatTask).getCrc64("audit");
        doReturn(new BigInteger("12863298021156289100")).when(this.resolverThreatTask).getCrc64("black");
        doReturn(new BigInteger("16292570364802992800")).when(this.resolverThreatTask).getCrc64("drop");
        doReturn(new BigInteger("15764284370007174481")).when(this.resolverThreatTask).getCrc64("white");

        final int idx = 0;

        customLists.setAuditList(audits);
        customLists.setBlackList(blackList);
        customLists.setDropList(dropList);
        customLists.setWhiteList(whiteList);
        policy.setCustomlists(customLists);

        doReturn(Collections.singletonList(policy)).when(mockConfiguration).getPolicies();
        doReturn(threat).when(mockThreats).computeIfAbsent(any(), any());

        this.resolverThreatTask.postProcessData(mockThreats);

        final InOrder customListTaskVerifier = Mockito.inOrder(this.resolverThreatTask, threat);
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(eq(audits), eq(Flag.audit), eq(idx), any());
        customListTaskVerifier.verify(threat).setSlot(idx, Flag.audit);
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(eq(blackList), eq(Flag.blacklist), eq(idx), any());
        customListTaskVerifier.verify(threat).setSlot(idx, Flag.blacklist);
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(eq(dropList), eq(Flag.drop), eq(idx), any());
        customListTaskVerifier.verify(threat).setSlot(idx, Flag.drop);
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(eq(whiteList), eq(Flag.whitelist), eq(idx), any());
        customListTaskVerifier.verify(threat).setSlot(idx, Flag.whitelist);

    }

    @Test
    public void testPostProcessingCustomListIsNull() throws Exception {
        // preparing
        final Policy policy = new Policy();
        final int idx = 0;

        doReturn(Collections.singletonList(policy)).when(mockConfiguration).getPolicies();

        this.resolverThreatTask.postProcessData(mockThreats);

        final InOrder customListTaskVerifier = Mockito.inOrder(this.resolverThreatTask);
        customListTaskVerifier.verify(this.resolverThreatTask, never()).handleCustomLists(any(), eq(Flag.audit), eq(idx), any());
        customListTaskVerifier.verify(this.resolverThreatTask, never()).handleCustomLists(any(), eq(Flag.blacklist), eq(idx), any());
        customListTaskVerifier.verify(this.resolverThreatTask, never()).handleCustomLists(any(), eq(Flag.drop), eq(idx), any());
        customListTaskVerifier.verify(this.resolverThreatTask, never()).handleCustomLists(any(), eq(Flag.whitelist), eq(idx), any());
    }

    @Test
    public void testPostProcessingCustomListIsEmpty() throws Exception {
        // preparing
        final Policy policy = new Policy();
        final PolicyCustomList customLists = new PolicyCustomList();
        final Set<String> audits = Collections.singleton("audit");
        doReturn(new BigInteger("1073251900497484785")).when(this.resolverThreatTask).getCrc64("audit");

        final Set<String> whiteList = Collections.singleton("white");
        doReturn(new BigInteger("15764284370007174481")).when(this.resolverThreatTask).getCrc64("white");
        final Threat threat = Mockito.mock(Threat.class);
        final int idx = 0;

        customLists.setAuditList(audits);
        customLists.setBlackList(new HashSet<>());
        customLists.setDropList(new HashSet<>());
        customLists.setWhiteList(whiteList);

        policy.setCustomlists(customLists);

        doReturn(Collections.singletonList(policy)).when(mockConfiguration).getPolicies();
        doReturn(threat).when(mockThreats).computeIfAbsent(any(), any());

        this.resolverThreatTask.postProcessData(mockThreats);

        final InOrder customListTaskVerifier = Mockito.inOrder(this.resolverThreatTask, threat);
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(eq(audits), eq(Flag.audit), eq(idx), any());
        customListTaskVerifier.verify(threat).setSlot(idx, Flag.audit);
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(any(), eq(Flag.blacklist), eq(idx), any());
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(any(), eq(Flag.drop), eq(idx), any());
        customListTaskVerifier.verify(this.resolverThreatTask).handleCustomLists(eq(whiteList), eq(Flag.whitelist), eq(idx), any());
        customListTaskVerifier.verify(threat).setSlot(idx, Flag.whitelist);

    }

}
