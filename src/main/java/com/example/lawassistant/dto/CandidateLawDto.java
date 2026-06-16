package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.LawType;

public record CandidateLawDto(
        Long lawId,
        String title,
        LawType lawType,
        String relevanceReason,
        double score
) {
}
