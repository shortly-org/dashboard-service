package io.shortly.dashboard.service;

import io.shortly.dashboard.dto.CountryCount;
import io.shortly.dashboard.dto.DayPoint;
import io.shortly.dashboard.dto.DayRangeRequest;
import io.shortly.dashboard.dto.LinkClicks;
import io.shortly.dashboard.dto.ReferrerCount;
import io.shortly.dashboard.dto.Summary;
import io.shortly.dashboard.exception.LinkNotFoundException;
import io.shortly.dashboard.model.BatchTotals;
import io.shortly.dashboard.model.ClickEvent;
import io.shortly.dashboard.repository.LinkRepository;
import io.shortly.dashboard.repository.StatsRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Counts clicks into the stats and reads them back, owner-scoped: one link or, when {@code code} is
 * null, all the owner's links.
 */
@Service
public class StatsService {

    private static final int TOP_LIMIT = 20;

    private static final int RETENTION_DAYS = 90;

    private final LinkRepository links;
    private final StatsRepository stats;

    StatsService(LinkRepository links, StatsRepository stats) {
        this.links = links;
        this.stats = stats;
    }

    public void count(List<ClickEvent> clicks) {
        BatchTotals totals = new BatchTotals();
        clicks.forEach(totals::add);
        stats.add(totals);
    }

    public Summary summary(UUID owner, @Nullable String code) {
        requireOwned(owner, code);
        return stats.summary(owner, code);
    }

    public List<DayPoint> daily(UUID owner, @Nullable String code, DayRangeRequest range) {
        requireOwned(owner, code);
        return stats.daily(owner, code, range.from(), range.to());
    }

    public List<CountryCount> countries(UUID owner, @Nullable String code, DayRangeRequest range) {
        requireOwned(owner, code);
        return stats.countries(owner, code, range.from(), range.to());
    }

    public List<ReferrerCount> referrers(UUID owner, @Nullable String code, DayRangeRequest range) {
        requireOwned(owner, code);
        return stats.referrers(owner, code, range.from(), range.to(), TOP_LIMIT);
    }

    public List<LinkClicks> topLinks(UUID owner, DayRangeRequest range) {
        return stats.topLinks(owner, range.from(), range.to(), TOP_LIMIT);
    }

    /** Deletes the country and referrer counts older than {@value #RETENTION_DAYS} days. */
    public void deleteExpired() {
        stats.deleteCountriesAndReferrersBefore(today().minusDays(RETENTION_DAYS));
    }

    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private void requireOwned(UUID owner, @Nullable String code) {
        if (code != null && !links.isOwned(owner, code)) {
            throw new LinkNotFoundException(code);
        }
    }
}
