package io.shortly.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final long FIRST_RETRY_MS = 1_000;
    private static final long LONGEST_WAIT_MS = 30_000;

    /** Retries a failed batch until it goes through, waiting 1 s at first and at most 30 s. */
    @Bean
    CommonErrorHandler clickErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(FIRST_RETRY_MS, 2);
        backOff.setMaxInterval(LONGEST_WAIT_MS);
        return new DefaultErrorHandler(backOff);
    }
}
