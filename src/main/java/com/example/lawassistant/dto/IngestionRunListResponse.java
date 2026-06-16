package com.example.lawassistant.dto;

import java.util.List;

public record IngestionRunListResponse(
        long totalCount,
        List<IngestionRunItemDto> items
) {
}
