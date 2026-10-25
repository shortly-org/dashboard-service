package io.shortly.dashboard.scheduler;

import io.shortly.dashboard.service.StatsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class StatsRetentionJob {

    private final StatsService stats;

    StatsRetentionJob(StatsService stats) {
        this.stats = stats;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    void deleteExpiredStats() {
        stats.deleteExpired();
    }
}
