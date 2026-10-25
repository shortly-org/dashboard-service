package io.shortly.dashboard.model;

import io.shortly.dashboard.util.CodeUtils;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

/** One click from the {@code clicks} topic; a click that breaks these rules is not counted. */
public record ClickEvent(
        String id,
        @NotNull @Pattern(regexp = "[0-9A-Za-z]{" + CodeUtils.LENGTH + "}") String code,
        @Positive @Max(253_402_300_799_999L) long at,
        @NotNull Outcome outcome,
        @Nullable @Pattern(regexp = "[A-Z]{2}") String country,
        @Nullable @Pattern(regexp = "[A-Za-z0-9.-]{1,253}") String ref) {

    public enum Outcome {
        SERVED,
        BLOCKED
    }
}
