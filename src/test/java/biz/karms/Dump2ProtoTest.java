package biz.karms;

import biz.karms.cache.pojo.BlacklistedRecord;
import biz.karms.cache.pojo.Rule;
import biz.karms.utils.CIDRUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.stream.IntStream;

/**
 * @author Michal Karm Babacek
 */
public class Dump2ProtoTest {

    @Test
    void iocGeneratorTest() throws IOException, InterruptedException {
        final MyCacheManagerProvider myCacheManagerProvider = new MyCacheManagerProvider(
                System.getProperty("SINKIT_HOTROD_HOST"),
                Integer.parseInt(System.getProperty("SINKIT_HOTROD_PORT")),
                10);

        System.setProperty("SINKIT_CUSTOMLIST_GENERATOR_INTERVAL_S", "0");
        System.setProperty("SINKIT_IOC_GENERATOR_INTERVAL_S", "5");
        System.setProperty("SINKIT_ALL_IOC_GENERATOR_INTERVAL_S", "0");
        System.setProperty("SINKIT_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S", "0");
        System.setProperty("SINKIT_WHITELIST_GENERATOR_INTERVAL_S", "0");

        final RemoteCache<String, BlacklistedRecord> blacklistCache = myCacheManagerProvider.getBlacklistCache();
        final RemoteCache<String, Rule> ruleRemoteCache;
        final RemoteCacheManager cacheManagerForIndexableCaches = myCacheManagerProvider.getCacheManagerForIndexableCaches();
        ruleRemoteCache = cacheManagerForIndexableCaches.getCache("infinispan_rules");
        if (ruleRemoteCache == null) {
            throw new RuntimeException("Cache 'infinispan_rules' not found. Please make sure the server is properly configured.");
        }

        ruleRemoteCache.clear();

        /*
        192.168.1.1/28 Client id 666
        192.169.1.1/28 Client id 667
        192.170.1.1/28 Client id 668
         */
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

        //final Dump2Proto dump2Proto = new Dump2Proto(myCacheManagerProvider);

    }
}
