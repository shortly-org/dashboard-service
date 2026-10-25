package io.shortly.dashboard;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@TestConfiguration(proxyBeanMethods = false)
class TestBeans {

    @Bean
    NewTopic clicksTopic() {
        return TopicBuilder.name("clicks").partitions(6).build();
    }

    @Bean
    DatabaseRoles databaseRoles() {
        return new DatabaseRoles(AbstractIntegrationTest.POSTGRES);
    }
}
