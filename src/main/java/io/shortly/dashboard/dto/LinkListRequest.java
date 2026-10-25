package io.shortly.dashboard.dto;

import io.shortly.dashboard.model.LinkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

/** Which links to list and which page of them; deleted links only when asked for by status. */
public record LinkListRequest(
        @Nullable LinkStatus status,
        @Min(0) @Schema(defaultValue = "0") Integer page,
        @Min(1)
                @Max(LinkListRequest.MAX_SIZE)
                @Schema(defaultValue = "" + LinkListRequest.DEFAULT_SIZE)
                Integer size) {

    public static final int DEFAULT_SIZE = 20;

    public static final int MAX_SIZE = 100;

    public LinkListRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = DEFAULT_SIZE;
        }
    }
}
