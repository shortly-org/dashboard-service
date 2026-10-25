package io.shortly.dashboard.repository;

import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_COUNTRY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_REFERRER;
import static io.shortly.dashboard.jooq.generated.Tables.LINKS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;

import io.shortly.dashboard.dto.CountryCount;
import io.shortly.dashboard.dto.DayPoint;
import io.shortly.dashboard.dto.LinkClicks;
import io.shortly.dashboard.dto.ReferrerCount;
import io.shortly.dashboard.dto.Summary;
import io.shortly.dashboard.model.BatchTotals;
import io.shortly.dashboard.model.BatchTotals.DayTotals;
import io.shortly.dashboard.model.BatchTotals.LinkDay;
import io.shortly.dashboard.model.BatchTotals.LinkDayValue;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Records;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class StatsRepository {

    private final DSLContext dsl;

    public StatsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** All-time totals; zeros for a link without clicks. */
    public Summary summary(UUID owner, @Nullable String code) {
        return dsl.select(
                        coalesce(sum(CLICK_DAILY.CLICKS).cast(Long.class), 0L),
                        coalesce(sum(CLICK_DAILY.BLOCKED).cast(Long.class), 0L),
                        max(CLICK_DAILY.LAST_CLICK_AT))
                .from(CLICK_DAILY)
                .where(ofLinks(CLICK_DAILY.CODE, owner, code))
                .fetchSingle(Records.mapping(Summary::new));
    }

    public List<DayPoint> daily(UUID owner, @Nullable String code, LocalDate from, LocalDate to) {
        return dsl.select(
                        CLICK_DAILY.DAY,
                        sum(CLICK_DAILY.CLICKS).cast(Long.class),
                        sum(CLICK_DAILY.BLOCKED).cast(Long.class))
                .from(CLICK_DAILY)
                .where(ofLinks(CLICK_DAILY.CODE, owner, code), CLICK_DAILY.DAY.between(from, to))
                .groupBy(CLICK_DAILY.DAY)
                .orderBy(CLICK_DAILY.DAY)
                .fetch(Records.mapping(DayPoint::new));
    }

    public List<CountryCount> countries(
            UUID owner, @Nullable String code, LocalDate from, LocalDate to) {
        var clicks = sum(CLICK_DAILY_COUNTRY.CLICKS).cast(Long.class);
        return dsl.select(CLICK_DAILY_COUNTRY.COUNTRY, clicks)
                .from(CLICK_DAILY_COUNTRY)
                .where(
                        ofLinks(CLICK_DAILY_COUNTRY.CODE, owner, code),
                        CLICK_DAILY_COUNTRY.DAY.between(from, to))
                .groupBy(CLICK_DAILY_COUNTRY.COUNTRY)
                .orderBy(clicks.desc(), CLICK_DAILY_COUNTRY.COUNTRY)
                .fetch(Records.mapping(CountryCount::new));
    }

    public List<ReferrerCount> referrers(
            UUID owner, @Nullable String code, LocalDate from, LocalDate to, int limit) {
        var clicks = sum(CLICK_DAILY_REFERRER.CLICKS).cast(Long.class);
        return dsl.select(CLICK_DAILY_REFERRER.REFERRER, clicks)
                .from(CLICK_DAILY_REFERRER)
                .where(
                        ofLinks(CLICK_DAILY_REFERRER.CODE, owner, code),
                        CLICK_DAILY_REFERRER.DAY.between(from, to))
                .groupBy(CLICK_DAILY_REFERRER.REFERRER)
                .orderBy(clicks.desc(), CLICK_DAILY_REFERRER.REFERRER)
                .limit(limit)
                .fetch(Records.mapping(ReferrerCount::new));
    }

    /** The owner's links with the most served clicks in the range, most first. */
    public List<LinkClicks> topLinks(UUID owner, LocalDate from, LocalDate to, int limit) {
        var clicks = sum(CLICK_DAILY.CLICKS).cast(Long.class);
        return dsl.select(CLICK_DAILY.CODE, clicks)
                .from(CLICK_DAILY)
                .where(ofLinks(CLICK_DAILY.CODE, owner, null), CLICK_DAILY.DAY.between(from, to))
                .groupBy(CLICK_DAILY.CODE)
                .orderBy(clicks.desc(), CLICK_DAILY.CODE)
                .limit(limit)
                .fetch(Records.mapping(LinkClicks::new));
    }

    /** The rows of the owner's links, or of the one with {@code code}. */
    private static Condition ofLinks(Field<String> statsCode, UUID owner, @Nullable String code) {
        Condition owned = LINKS.OWNER_ID.eq(owner);
        if (code != null) {
            owned = owned.and(LINKS.CODE.eq(code));
        }
        return statsCode.in(select(LINKS.CODE).from(LINKS).where(owned));
    }

    /** Adds one batch's totals to the rows in one transaction. */
    public void add(BatchTotals totals) {
        List<Query> upserts = new ArrayList<>();
        totals.daily().forEach((key, day) -> upserts.add(addDaily(key, day)));
        totals.countries().forEach((key, clicks) -> upserts.add(addCountry(key, clicks)));
        totals.referrers().forEach((key, clicks) -> upserts.add(addReferrer(key, clicks)));
        if (upserts.isEmpty()) {
            return;
        }
        dsl.transaction(configuration -> configuration.dsl().batch(upserts).execute());
    }

    private Query addDaily(LinkDay key, DayTotals day) {
        return dsl.insertInto(CLICK_DAILY)
                .set(CLICK_DAILY.CODE, key.code())
                .set(CLICK_DAILY.DAY, key.day())
                .set(CLICK_DAILY.CLICKS, day.served())
                .set(CLICK_DAILY.BLOCKED, day.blocked())
                .set(CLICK_DAILY.LAST_CLICK_AT, day.lastClickAt())
                .onConflict(CLICK_DAILY.CODE, CLICK_DAILY.DAY)
                .doUpdate()
                .set(CLICK_DAILY.CLICKS, CLICK_DAILY.CLICKS.plus(excluded(CLICK_DAILY.CLICKS)))
                .set(CLICK_DAILY.BLOCKED, CLICK_DAILY.BLOCKED.plus(excluded(CLICK_DAILY.BLOCKED)))
                .set(
                        CLICK_DAILY.LAST_CLICK_AT,
                        greatest(CLICK_DAILY.LAST_CLICK_AT, excluded(CLICK_DAILY.LAST_CLICK_AT)));
    }

    private Query addCountry(LinkDayValue key, long clicks) {
        return dsl.insertInto(CLICK_DAILY_COUNTRY)
                .set(CLICK_DAILY_COUNTRY.CODE, key.code())
                .set(CLICK_DAILY_COUNTRY.DAY, key.day())
                .set(CLICK_DAILY_COUNTRY.COUNTRY, key.value())
                .set(CLICK_DAILY_COUNTRY.CLICKS, clicks)
                .onConflict(
                        CLICK_DAILY_COUNTRY.CODE,
                        CLICK_DAILY_COUNTRY.DAY,
                        CLICK_DAILY_COUNTRY.COUNTRY)
                .doUpdate()
                .set(
                        CLICK_DAILY_COUNTRY.CLICKS,
                        CLICK_DAILY_COUNTRY.CLICKS.plus(excluded(CLICK_DAILY_COUNTRY.CLICKS)));
    }

    private Query addReferrer(LinkDayValue key, long clicks) {
        return dsl.insertInto(CLICK_DAILY_REFERRER)
                .set(CLICK_DAILY_REFERRER.CODE, key.code())
                .set(CLICK_DAILY_REFERRER.DAY, key.day())
                .set(CLICK_DAILY_REFERRER.REFERRER, key.value())
                .set(CLICK_DAILY_REFERRER.CLICKS, clicks)
                .onConflict(
                        CLICK_DAILY_REFERRER.CODE,
                        CLICK_DAILY_REFERRER.DAY,
                        CLICK_DAILY_REFERRER.REFERRER)
                .doUpdate()
                .set(
                        CLICK_DAILY_REFERRER.CLICKS,
                        CLICK_DAILY_REFERRER.CLICKS.plus(excluded(CLICK_DAILY_REFERRER.CLICKS)));
    }

    /**
     * Deletes the country and referrer rows from before {@code oldestDay}, without the pool's
     * transaction timeout.
     */
    public void deleteCountriesAndReferrersBefore(LocalDate oldestDay) {
        dsl.transaction(
                configuration -> {
                    DSLContext tx = configuration.dsl();
                    tx.execute("SET LOCAL transaction_timeout = 0");
                    tx.deleteFrom(CLICK_DAILY_COUNTRY)
                            .where(CLICK_DAILY_COUNTRY.DAY.lt(oldestDay))
                            .execute();
                    tx.deleteFrom(CLICK_DAILY_REFERRER)
                            .where(CLICK_DAILY_REFERRER.DAY.lt(oldestDay))
                            .execute();
                });
    }
}
