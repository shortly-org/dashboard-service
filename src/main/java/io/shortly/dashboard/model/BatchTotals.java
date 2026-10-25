package io.shortly.dashboard.model;

import io.shortly.dashboard.model.ClickEvent.Outcome;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The totals of one batch of clicks, per row of the stats tables. */
public final class BatchTotals {

    /** A row of {@code click_daily}. */
    public record LinkDay(String code, LocalDate day) {}

    /** A row of {@code click_daily_country} or {@code click_daily_referrer}. */
    public record LinkDayValue(String code, LocalDate day, String value) {}

    /** What a batch adds to one {@code click_daily} row. */
    public record DayTotals(long served, long blocked, long lastClickMillis) {

        static DayTotals of(ClickEvent click) {
            if (click.outcome() == Outcome.SERVED) {
                return new DayTotals(1, 0, click.at());
            }
            return new DayTotals(0, 1, click.at());
        }

        DayTotals plus(DayTotals other) {
            return new DayTotals(
                    served + other.served,
                    blocked + other.blocked,
                    Math.max(lastClickMillis, other.lastClickMillis));
        }

        public Instant lastClickAt() {
            return Instant.ofEpochMilli(lastClickMillis);
        }
    }

    private final Map<LinkDay, DayTotals> daily = new HashMap<>();
    private final Map<LinkDayValue, Long> countries = new HashMap<>();
    private final Map<LinkDayValue, Long> referrers = new HashMap<>();

    /**
     * Adds a click to the day it happened, UTC; countries and referrers count served clicks only.
     */
    public void add(ClickEvent click) {
        LocalDate day = LocalDate.ofInstant(Instant.ofEpochMilli(click.at()), ZoneOffset.UTC);
        daily.merge(new LinkDay(click.code(), day), DayTotals.of(click), DayTotals::plus);
        if (click.outcome() != Outcome.SERVED) {
            return;
        }
        String country = click.country();
        if (country != null) {
            countries.merge(new LinkDayValue(click.code(), day, country), 1L, Long::sum);
        }
        String ref = click.ref();
        if (ref != null) {
            referrers.merge(new LinkDayValue(click.code(), day, referrerSite(ref)), 1L, Long::sum);
        }
    }

    public Map<LinkDay, DayTotals> daily() {
        return Collections.unmodifiableMap(daily);
    }

    public Map<LinkDayValue, Long> countries() {
        return Collections.unmodifiableMap(countries);
    }

    public Map<LinkDayValue, Long> referrers() {
        return Collections.unmodifiableMap(referrers);
    }

    /** The site a referrer counts under: lower-case, without a leading {@code www.}. */
    private static String referrerSite(String host) {
        String site = host.toLowerCase(Locale.ROOT);
        if (site.startsWith("www.") && site.length() > 4) {
            return site.substring(4);
        }
        return site;
    }
}
