package io.shortly.dashboard;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** The Postgres and Kafka every integration test shares, started once, and the one context. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestBeans.class)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("local/database-roles.sql"),
                            "/docker-entrypoint-initdb.d/database-roles.sql");

    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> DatabaseRoles.APP);
        registry.add("spring.datasource.password", () -> DatabaseRoles.APP);
        registry.add("spring.flyway.user", () -> DatabaseRoles.OWNER);
        registry.add("spring.flyway.password", () -> DatabaseRoles.OWNER);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
