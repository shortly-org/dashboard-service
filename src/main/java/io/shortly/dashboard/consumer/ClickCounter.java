package io.shortly.dashboard.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.shortly.dashboard.model.ClickEvent;
import io.shortly.dashboard.service.StatsService;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Counts the clicks from the {@code clicks} topic into the stats tables. */
@Component
class ClickCounter {

    static final String CLICKS = "clicks";

    private static final Logger LOG = LoggerFactory.getLogger(ClickCounter.class);

    private final StatsService stats;
    private final Validator validator;
    private final Counter discarded;

    ClickCounter(StatsService stats, Validator validator, MeterRegistry meters) {
        this.stats = stats;
        this.validator = validator;
        this.discarded = meters.counter("shortly.stats.discarded");
    }

    @KafkaListener(topics = CLICKS)
    void onClicks(List<ConsumerRecord<String, @Nullable ClickEvent>> records) {
        List<ClickEvent> clicks = new ArrayList<>(records.size());
        for (ConsumerRecord<String, @Nullable ClickEvent> record : records) {
            ClickEvent click = record.value();
            if (click == null || !validator.validate(click).isEmpty()) {
                discard(record);
            } else {
                clicks.add(click);
            }
        }
        stats.count(clicks);
    }

    private void discard(ConsumerRecord<String, @Nullable ClickEvent> record) {
        discarded.increment();
        LOG.warn(
                "discarding a record that is not a usable click {}-{}@{}",
                record.topic(),
                record.partition(),
                record.offset());
    }
}
