package com.example.lawassistant.dto;

import java.util.List;

public record SearchLogListResponse(
        long total,
        List<SearchLogItemDto> items
) {
}
