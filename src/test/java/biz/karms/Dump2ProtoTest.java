package biz.karms;

import biz.karms.crc64java.CRC64;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.ejb.cache.pojo.Rule;
import biz.karms.utils.CIDRUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.Search;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.junit.Ignore;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.testng.Assert.assertNotNull;

/**
 * @author Michal Karm Babacek
 */
public class Dump2ProtoTest {

    private static final Logger log = Logger.getLogger(Dump2ProtoTest.class.getName());

    @DataProvider(name = "domainFilesProvider")
    public Object[][] domainFilesProvider() {
        return new Object[][]{
                {String.class, "100_domains.txt"},
                {String.class, "1000_domains.txt"}//,
                //{String.class, "10000_domains.txt"}
        };
    }

    @Ignore
    @Test(dataProvider = "domainFilesProvider", enabled = false)
    void iocGeneratorTest(Class clazz, final String domainsFile) throws IOException, InterruptedException {
        log.info("Thread " + Thread.currentThread().getName() + ": domainsFile: " + domainsFile);

        final MyCacheManagerProvider myCacheManagerProvider = new MyCacheManagerProvider(
                System.getProperty("D2P_HOTROD_HOST"),
                Integer.parseInt(System.getProperty("D2P_HOTROD_PORT")),
                10);

        System.setProperty("D2P_CUSTOMLIST_GENERATOR_INTERVAL_S", "0");
        System.setProperty("D2P_IOC_GENERATOR_INTERVAL_S", "5");
        System.setProperty("D2P_ALL_IOC_GENERATOR_INTERVAL_S", "0");
        System.setProperty("D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S", "0");
        System.setProperty("D2P_WHITELIST_GENERATOR_INTERVAL_S", "0");

        final RemoteCache<String, BlacklistedRecord> blacklistCache = myCacheManagerProvider.getBlacklistCache();
        final RemoteCache<String, Rule> ruleRemoteCache;
        final RemoteCacheManager cacheManagerForIndexableCaches = myCacheManagerProvider.getCacheManagerForIndexableCaches();
        ruleRemoteCache = cacheManagerForIndexableCaches.getCache("infinispan_rules");
        if (ruleRemoteCache == null) {
            throw new RuntimeException("Cache 'infinispan_rules' not found. Please make sure the server is properly configured.");
        }

        ruleRemoteCache.clear();

        //192.168.1.1/28 Client id 666
        //192.169.1.1/28 Client id 667
        //192.170.1.1/28 Client id 668

        IntStream.range(0, 3).forEach(num -> {
                    final Rule rule = new Rule();

                    //CIDR
                    final String cidr = String.format("192.%d.1.1/28", 168 + num);

                    ImmutablePair<String, String> startEndAddresses = null;
                    try {
                        startEndAddresses = CIDRUtils.getStartEndAddresses(cidr);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                    final String startAddress = startEndAddresses.getLeft();
                    final String endAddress = startEndAddresses.getRight();
                    rule.setCidrAddress(cidr);

                    //Client id
                    rule.setCustomerId(666 + num);

                    rule.setStartAddress(startAddress);
                    rule.setEndAddress(endAddress);
                    rule.setSources(new HashMap<String, String>() {
                        {
                            //this feed name will be "unique" to the rule
                            put(num + "-some-feed-to-sink", "S");
                            //these are the the same for all rules
                            put("feed-to-log", "L");
                            put("feed-to-sink", "S");
                            put("some-disabled-feed", "D");
                        }
                    });
                    ruleRemoteCache.put(rule.getStartAddress(), rule);
                    System.out.format("Added client id: %d, Start address %s, End address %s\n", rule.getCustomerId(), startAddress, endAddress);
                }
        );

        blacklistCache.clear();
        List<String> fqdns = new ArrayList<>(1000);
        System.out.print("Progress:");
        try (Stream<String> stream = Files.lines(Paths.get(domainsFile))) {
            stream.forEach(fqdn -> {
                fqdns.add(fqdn);
                final String fqdnHashed = DigestUtils.md5Hex(fqdn);
                final BigInteger crc64 = CRC64.getInstance().crc64BigInteger(fqdn.getBytes());

                final HashMap<String, ImmutablePair<String, String>> feedToType = new HashMap<>();
                feedToType.put("2-some-feed-to-sink", new ImmutablePair<>("fqdn", "bla bla"));
                feedToType.put("something-nobody-has-configured", new ImmutablePair<>("fqdn", "bla bla"));
                final HashMap<String, Integer> accuracy = new HashMap<>();
                accuracy.put("feed", 20);
                accuracy.put("lobotomie", 30);
                final HashMap<String, HashMap<String, Integer>> feedAccuracy = new HashMap<>();
                feedAccuracy.put("2-some-feed-to-sink", accuracy);
                final BlacklistedRecord blacklistedRecord = new BlacklistedRecord(fqdnHashed, crc64, Calendar.getInstance(), feedToType, feedAccuracy, Boolean.FALSE);
                blacklistCache.put(fqdnHashed, blacklistedRecord);
                System.out.print('.');
                System.out.flush();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.print('\n');

        System.out.format("IoC cache stats: %s\n", blacklistCache.stats().getStatsMap());

        // Client 192.168.1.12 from 192.168.1.1/28 Client id 666
        // Asks about adjmp.xyz

        final BlacklistedRecord ioc = blacklistCache.get(DigestUtils.md5Hex(fqdns.get(0)));

        assertNotNull(ioc, "IoC was not supposed to be nul at this point.");
        // What to do with that domain based on client IP address and rules?
        final QueryFactory qf = Search.getQueryFactory(ruleRemoteCache);
        String clientIPAsNumber = CIDRUtils.getStartEndAddresses("192.168.1.12").getLeft();
        Query query = qf.from(Rule.class)
                .having("startAddress").lte(clientIPAsNumber)
                .and()
                .having("endAddress").gte(clientIPAsNumber)
                .and()
                .having("sources.feedUid").in(ioc.getSources().keySet())
                .toBuilder().build();
        List<Rule> resultRules = query.list();
        System.out.format("Rule record relevant to the client %s and fqdn %s is:\n", "192.168.1.12", fqdns.get(0));
        resultRules.forEach(System.out::println);
        clientIPAsNumber = CIDRUtils.getStartEndAddresses("192.170.1.12").getLeft();
        query = qf.from(Rule.class)
                .having("startAddress").lte(clientIPAsNumber)
                .and()
                .having("endAddress").gte(clientIPAsNumber)
                .and()
                .having("sources.feedUid").in(ioc.getSources().keySet())
                .toBuilder().build();
        resultRules = query.list();
        System.out.format("Rule record relevant to the client %s and fqdn %s is:\n", "192.170.1.12", fqdns.get(0));
        resultRules.forEach(System.out::println);
    }
}
