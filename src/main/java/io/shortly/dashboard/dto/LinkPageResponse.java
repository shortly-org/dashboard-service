package io.shortly.dashboard.dto;

import java.util.List;

public record LinkPageResponse(
        List<LinkResponse> items, int page, int size, long totalItems, long totalPages) {}
