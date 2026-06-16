package com.example.lawassistant.dto;

import java.util.List;

public record LawListResponse(
        long total,
        int page,
        int size,
        List<LawSummaryDto> items
) {

    public LawListResponse(long total, List<LawSummaryDto> items) {
        this(total, 1, items.size(), items);
    }
}
