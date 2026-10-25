package io.shortly.dashboard.model;

import java.time.Instant;
import java.util.UUID;

public record Link(
        String code,
        UUID ownerId,
        String targetUrl,
        LinkStatus status,
        int version,
        Instant createdAt,
        Instant updatedAt) {}
