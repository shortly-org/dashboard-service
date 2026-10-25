package io.shortly.dashboard.dto;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/**
 * The UTC days a stats read covers, both included; the last {@value #DEFAULT_DAYS} days by default.
 */
public record DayRangeRequest(
        @DateTimeFormat(iso = ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = ISO.DATE) LocalDate to) {

    public static final int DEFAULT_DAYS = 30;

    public DayRangeRequest {
        if (to == null) {
            to = LocalDate.now(ZoneOffset.UTC);
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS - 1);
        }
    }

    @AssertTrue(message = "from must not be after to")
    public boolean isOrdered() {
        return !from.isAfter(to);
    }
}
