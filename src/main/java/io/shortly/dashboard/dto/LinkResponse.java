package io.shortly.dashboard.dto;

import io.shortly.dashboard.model.Link;
import io.shortly.dashboard.model.LinkStatus;
import java.time.Instant;
import org.springframework.web.util.UriComponentsBuilder;

public record LinkResponse(
        String code,
        String shortUrl,
        String targetUrl,
        LinkStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static LinkResponse from(Link link, String shortBaseUrl) {
        return new LinkResponse(
                link.code(),
                UriComponentsBuilder.fromUriString(shortBaseUrl)
                        .pathSegment(link.code())
                        .toUriString(),
                link.targetUrl(),
                link.status(),
                link.version(),
                link.createdAt(),
                link.updatedAt());
    }
}
