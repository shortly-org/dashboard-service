package io.shortly.dashboard.consumer;

import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_COUNTRY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_REFERRER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.shortly.dashboard.AbstractIntegrationTest;
import io.shortly.dashboard.DatabaseRoles;
import io.shortly.dashboard.model.ClickEvent;
import io.shortly.dashboard.model.ClickEvent.Outcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

class ClickCounterTest extends AbstractIntegrationTest {

    private static final Instant CLICKED_AT =
            Instant.now()
                    .truncatedTo(ChronoUnit.DAYS)
                    .minus(1, ChronoUnit.DAYS)
                    .plus(10, ChronoUnit.HOURS);
    private static final LocalDate DAY = CLICKED_AT.atZone(ZoneOffset.UTC).toLocalDate();
    private static final JsonMapper JSON = new JsonMapper();

    @Autowired DatabaseRoles roles;
    @Autowired MeterRegistry meters;

    @BeforeEach
    void prepare() {
        roles.resetStatsTables();
    }

    @Test
    void clicksFromKafkaAreCountedPerDayCountryAndReferrer() {
        Instant newest = CLICKED_AT.plus(Duration.ofMinutes(58));
        try (var producer = producer()) {
            send(
                    producer,
                    "abcabcab1",
                    click("abcabcab1", CLICKED_AT, Outcome.SERVED, "CH", "t.co"));
            send(
                    producer,
                    "abcabcab1",
                    click("abcabcab1", CLICKED_AT, Outcome.SERVED, "CH", "www.T.co"));
            send(producer, "abcabcab1", click("abcabcab1", newest, Outcome.SERVED, "DE", null));
            send(
                    producer,
                    "abcabcab1",
                    click("abcabcab1", CLICKED_AT, Outcome.BLOCKED, "CH", "t.co"));
            producer.flush();
        }

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(
                        () -> {
                            var daily =
                                    roles.owner()
                                            .fetchOne(
                                                    CLICK_DAILY, CLICK_DAILY.CODE.eq("abcabcab1"));
                            assertThat(daily).isNotNull();
                            assertThat(daily.getDay()).isEqualTo(DAY);
                            assertThat(daily.getClicks()).isEqualTo(3L);
                            assertThat(daily.getBlocked()).isEqualTo(1L);
                            assertThat(daily.getLastClickAt()).isEqualTo(newest);
                        });
        assertThat(countriesOf("abcabcab1"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("CH", 2L, "DE", 1L));
        assertThat(referrersOf("abcabcab1")).containsExactlyEntriesOf(Map.of("t.co", 2L));
    }

    @Test
    void unusableRecordsAreSkippedAndCountedWithoutStoppingTheRest() {
        double discardedBefore = discarded();
        try (var producer = producer()) {
            send(producer, "abcabcab3", "not json".getBytes(UTF_8));
            send(producer, "abcabcab3", (byte[]) null);
            send(producer, "abcabcab3", line(null, CLICKED_AT, "SERVED", null, null));
            send(producer, "abcabcab3", line("toolongcode", CLICKED_AT, "SERVED", null, null));
            send(producer, "abcabcab3", line("abcabcab3", CLICKED_AT, "SERVED", "C\u0000", null));
            send(
                    producer,
                    "abcabcab3",
                    line("abcabcab3", CLICKED_AT, "SERVED", null, "x".repeat(254)));
            send(producer, "abcabcab3", line("abcabcab3", CLICKED_AT, "SERVED", "CH", "t.co"));
            producer.flush();
        }

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(dailyClicksOf("abcabcab3")).isEqualTo(1L));
        assertThat(discarded() - discardedBefore).isEqualTo(6.0);
    }

    @Test
    void aBatchTheDatabaseRefusesIsRetriedUntilItGoesThrough() {
        long failuresBefore = failedDeliveries();
        roles.owner().execute("REVOKE INSERT, UPDATE ON click_daily FROM dashboard_app");
        try {
            try (var producer = producer()) {
                send(
                        producer,
                        "abcabcab5",
                        click("abcabcab5", CLICKED_AT, Outcome.SERVED, null, null));
                producer.flush();
            }
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> assertThat(failedDeliveries()).isGreaterThan(failuresBefore));
        } finally {
            roles.owner().execute("GRANT INSERT, UPDATE ON click_daily TO dashboard_app");
        }

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(dailyClicksOf("abcabcab5")).isEqualTo(1L));
    }

    private double discarded() {
        return meters.counter("shortly.stats.discarded").count();
    }

    private long failedDeliveries() {
        return meters.find("spring.kafka.listener").tag("result", "failure").timers().stream()
                .mapToLong(Timer::count)
                .sum();
    }

    private static byte[] line(
            @Nullable String code,
            Instant at,
            String outcome,
            @Nullable String country,
            @Nullable String ref) {
        Map<String, @Nullable Object> line = new LinkedHashMap<>();
        line.put("id", UUID.randomUUID().toString());
        line.put("code", code);
        line.put("at", at.toEpochMilli());
        line.put("outcome", outcome);
        line.put("country", country);
        line.put("ref", ref);
        return JSON.writeValueAsBytes(line);
    }

    private static ClickEvent click(
            String code, Instant at, Outcome outcome, String country, String ref) {
        return new ClickEvent(
                UUID.randomUUID().toString(), code, at.toEpochMilli(), outcome, country, ref);
    }

    private long dailyClicksOf(String code) {
        var row = roles.owner().fetchOne(CLICK_DAILY, CLICK_DAILY.CODE.eq(code));
        if (row == null) {
            return 0;
        }
        return row.getClicks();
    }

    private Map<String, Long> countriesOf(String code) {
        return roles.owner()
                .select(CLICK_DAILY_COUNTRY.COUNTRY, CLICK_DAILY_COUNTRY.CLICKS)
                .from(CLICK_DAILY_COUNTRY)
                .where(CLICK_DAILY_COUNTRY.CODE.eq(code))
                .fetchMap(CLICK_DAILY_COUNTRY.COUNTRY, CLICK_DAILY_COUNTRY.CLICKS);
    }

    private Map<String, Long> referrersOf(String code) {
        return roles.owner()
                .select(CLICK_DAILY_REFERRER.REFERRER, CLICK_DAILY_REFERRER.CLICKS)
                .from(CLICK_DAILY_REFERRER)
                .where(CLICK_DAILY_REFERRER.CODE.eq(code))
                .fetchMap(CLICK_DAILY_REFERRER.REFERRER, CLICK_DAILY_REFERRER.CLICKS);
    }

    private static void send(
            KafkaProducer<String, byte[]> producer, String code, ClickEvent click) {
        send(producer, code, JSON.writeValueAsBytes(click));
    }

    private static void send(
            KafkaProducer<String, byte[]> producer, String code, byte @Nullable [] value) {
        producer.send(new ProducerRecord<>(ClickCounter.CLICKS, code, value));
    }

    private static KafkaProducer<String, byte[]> producer() {
        return new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(),
                new ByteArraySerializer());
    }
}
