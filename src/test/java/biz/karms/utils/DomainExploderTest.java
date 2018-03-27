package biz.karms.utils;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author Michal Karm Babacek
 */
public class DomainExploderTest {

    private static final Logger log = Logger.getLogger(DomainExploderTest.class.getName());

    @DataProvider(name = "domainFilesProvider")
    public Object[][] domainFilesProvider() {
        return new Object[][]{
                {String.class, "many_levels.txt"}//,
                //{String.class, "1000_domains.txt"}//,
                //{String.class, "10000_domains.txt"}
        };
    }

    // ??? String.join(",", array);
    private static List<String> explodeDomain(final String fqdn) {
        final String[] subs = fqdn.split("\\.");
        final List<String> result = new ArrayList<>(subs.length);
        if (subs.length > 2) {
            final StringBuilder sb = new StringBuilder(fqdn.length());
            for (int i = subs.length - 2; i >= 0; i--) {
                sb.setLength(fqdn.length());
                for (int j = i; j < subs.length - 1; j++) {
                    sb.append(subs[j]);
                    if (j + 1 <= subs.length - 1 || j == i) {
                        sb.append(".");
                    }
                }
                sb.append(subs[subs.length - 1]);
                result.add(sb.toString());
                sb.setLength(0);
            }
            return result;
        }
        return Collections.singletonList(fqdn);
    }

    @Test(dataProvider = "domainFilesProvider")
    void explodeTest(Class clazz, final String domainsFile) throws IOException, InterruptedException {
        log.info("domainsFile: " + domainsFile);
        System.out.println("Progress:");
        try (Stream<String> stream = Files.lines(Paths.get(domainsFile))) {
            stream.forEach(fqdn -> {
                System.out.println(fqdn);
                explodeDomain(fqdn).forEach(System.out::println);
                System.out.println("====");
                System.out.flush();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.print('\n');
    }
}
