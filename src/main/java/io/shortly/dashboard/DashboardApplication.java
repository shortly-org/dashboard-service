package io.shortly.dashboard;

import java.security.Security;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DashboardApplication {

    public static void main(String[] args) {
        // Aurora endpoints move on failover; AWS recommends a DNS cache TTL under 30 s.
        Security.setProperty("networkaddress.cache.ttl", "5");
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
        SpringApplication.run(DashboardApplication.class, args);
    }
}
