package io.shortly.dashboard;

import static io.shortly.dashboard.TestTokens.userToken;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_COUNTRY;
import static io.shortly.dashboard.jooq.generated.Tables.CLICK_DAILY_REFERRER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.shortly.dashboard.model.LinkStatus;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class StatsApiTest extends AbstractIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final String DELETED = "deletedl1";
    private static final String ACTIVE = "activeli1";
    private static final String BOBS = "bobslink1";

    @Autowired MockMvc mvc;
    @Autowired DatabaseRoles roles;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void seed() {
        roles.resetLinkTables();
        roles.resetStatsTables();
        roles.insertLink(DELETED, alice, LinkStatus.DELETED, 2);
        roles.insertLink(ACTIVE, alice, LinkStatus.ACTIVE, 1);
        roles.insertLink(BOBS, bob, LinkStatus.ACTIVE, 1);
        daily(DELETED, TODAY, 5, 1);
        daily(DELETED, TODAY.minusDays(400), 100, 0);
        daily(ACTIVE, TODAY, 3, 0);
        daily(BOBS, TODAY, 1000, 0);
        country(DELETED, "CH", 4);
        country(ACTIVE, "CH", 1);
        country(ACTIVE, "DE", 2);
        referrer(DELETED, "t.co", 5);
    }

    @Test
    void oneLinksStats() throws Exception {
        mvc.perform(get("/api/v1/links/" + DELETED + "/stats/summary").with(userToken(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicks").value(105))
                .andExpect(jsonPath("$.blocked").value(1));
        mvc.perform(get("/api/v1/links/" + DELETED + "/stats/daily").with(userToken(alice)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clicks").value(5));
        mvc.perform(get("/api/v1/links/" + DELETED + "/stats/referrers").with(userToken(alice)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].referrer").value("t.co"))
                .andExpect(jsonPath("$[0].clicks").value(5));
        mvc.perform(
                        get("/api/v1/links/" + DELETED + "/stats/daily")
                                .param("from", TODAY.toString())
                                .param("to", TODAY.minusDays(1).toString())
                                .with(userToken(alice)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountStatsCoverEveryLinkIncludingDeleted() throws Exception {
        mvc.perform(get("/api/v1/stats/summary").with(userToken(alice)))
                .andExpect(jsonPath("$.clicks").value(108));
        mvc.perform(get("/api/v1/stats/countries").with(userToken(alice)))
                .andExpect(jsonPath("$[0].country").value("CH"))
                .andExpect(jsonPath("$[0].clicks").value(5));
        mvc.perform(get("/api/v1/stats/links").with(userToken(alice)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value(DELETED))
                .andExpect(jsonPath("$[1].code").value(ACTIVE));
    }

    @Test
    void someoneElsesLinkIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/links/" + BOBS + "/stats/summary").with(userToken(alice)))
                .andExpect(status().isNotFound());
    }

    private void daily(String code, LocalDate day, long clicks, long blocked) {
        roles.owner()
                .insertInto(CLICK_DAILY)
                .set(CLICK_DAILY.CODE, code)
                .set(CLICK_DAILY.DAY, day)
                .set(CLICK_DAILY.CLICKS, clicks)
                .set(CLICK_DAILY.BLOCKED, blocked)
                .set(CLICK_DAILY.LAST_CLICK_AT, day.atTime(12, 0).toInstant(ZoneOffset.UTC))
                .execute();
    }

    private void country(String code, String country, long clicks) {
        roles.owner()
                .insertInto(CLICK_DAILY_COUNTRY)
                .set(CLICK_DAILY_COUNTRY.CODE, code)
                .set(CLICK_DAILY_COUNTRY.DAY, TODAY)
                .set(CLICK_DAILY_COUNTRY.COUNTRY, country)
                .set(CLICK_DAILY_COUNTRY.CLICKS, clicks)
                .execute();
    }

    private void referrer(String code, String referrer, long clicks) {
        roles.owner()
                .insertInto(CLICK_DAILY_REFERRER)
                .set(CLICK_DAILY_REFERRER.CODE, code)
                .set(CLICK_DAILY_REFERRER.DAY, TODAY)
                .set(CLICK_DAILY_REFERRER.REFERRER, referrer)
                .set(CLICK_DAILY_REFERRER.CLICKS, clicks)
                .execute();
    }
}
