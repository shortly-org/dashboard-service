package io.shortly.dashboard.dto;

import io.shortly.dashboard.model.LinkStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull Status status) {

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public LinkStatus toLinkStatus() {
        return switch (status) {
            case ACTIVE -> LinkStatus.ACTIVE;
            case INACTIVE -> LinkStatus.INACTIVE;
        };
    }
}
