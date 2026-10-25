package io.shortly.dashboard.controller;

import io.shortly.dashboard.dto.CountryCount;
import io.shortly.dashboard.dto.DayPoint;
import io.shortly.dashboard.dto.DayRangeRequest;
import io.shortly.dashboard.dto.LinkClicks;
import io.shortly.dashboard.dto.ReferrerCount;
import io.shortly.dashboard.dto.Summary;
import io.shortly.dashboard.model.CurrentUser;
import io.shortly.dashboard.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Statistics", description = "Usage statistics for your short links")
class StatsController {

    private final StatsService stats;

    StatsController(StatsService stats) {
        this.stats = stats;
    }

    @Operation(summary = "Total clicks and last click of a link")
    @GetMapping("/links/{code}/stats/summary")
    Summary summary(@AuthenticationPrincipal CurrentUser user, @PathVariable String code) {
        return stats.summary(user.id(), code);
    }

    @Operation(summary = "Clicks per day of a link")
    @GetMapping("/links/{code}/stats/daily")
    List<DayPoint> daily(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String code,
            @Valid @ParameterObject DayRangeRequest range) {
        return stats.daily(user.id(), code, range);
    }

    @Operation(summary = "Clicks per country of a link, most clicks first")
    @GetMapping("/links/{code}/stats/countries")
    List<CountryCount> countries(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String code,
            @Valid @ParameterObject DayRangeRequest range) {
        return stats.countries(user.id(), code, range);
    }

    @Operation(summary = "Top referrers of a link, most clicks first")
    @GetMapping("/links/{code}/stats/referrers")
    List<ReferrerCount> referrers(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable String code,
            @Valid @ParameterObject DayRangeRequest range) {
        return stats.referrers(user.id(), code, range);
    }

    @Operation(summary = "Total clicks and last click across all your links")
    @GetMapping("/stats/summary")
    Summary accountSummary(@AuthenticationPrincipal CurrentUser user) {
        return stats.summary(user.id(), null);
    }

    @Operation(summary = "Clicks per day across all your links")
    @GetMapping("/stats/daily")
    List<DayPoint> accountDaily(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @ParameterObject DayRangeRequest range) {
        return stats.daily(user.id(), null, range);
    }

    @Operation(summary = "Clicks per country across all your links, most clicks first")
    @GetMapping("/stats/countries")
    List<CountryCount> accountCountries(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @ParameterObject DayRangeRequest range) {
        return stats.countries(user.id(), null, range);
    }

    @Operation(summary = "Top referrers across all your links, most clicks first")
    @GetMapping("/stats/referrers")
    List<ReferrerCount> accountReferrers(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @ParameterObject DayRangeRequest range) {
        return stats.referrers(user.id(), null, range);
    }

    @Operation(summary = "Your links with the most clicks, most first")
    @GetMapping("/stats/links")
    List<LinkClicks> topLinks(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @ParameterObject DayRangeRequest range) {
        return stats.topLinks(user.id(), range);
    }
}
