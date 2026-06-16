package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.LawType;

public record LawSummaryDto(
        Long lawId,
        String title,
        LawType lawType,
        String lawNumber,
        long articleCount,
        long revisionCount
) {

    public LawSummaryDto(Long lawId, String title, LawType lawType, String lawNumber) {
        this(lawId, title, lawType, lawNumber, 0L, 0L);
    }
}
