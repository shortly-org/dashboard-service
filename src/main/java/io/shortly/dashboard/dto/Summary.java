package io.shortly.dashboard.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record Summary(long clicks, long blocked, @Nullable Instant lastClickAt) {}
