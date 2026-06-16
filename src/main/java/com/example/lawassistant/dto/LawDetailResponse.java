package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.LawType;
import java.time.LocalDate;
import java.util.List;

public record LawDetailResponse(
        Long lawId,
        String slug,
        String title,
        LawType lawType,
        String lawNumber,
        LocalDate enactedAt,
        LocalDate lastAmendedAt,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String snapshotVersion,
        List<ArticleResponse> articles,
        EffectiveBasisDto effectiveBasis
) {
    public LawDetailResponse(
            Long lawId,
            String slug,
            String title,
            LawType lawType,
            String lawNumber,
            LocalDate enactedAt,
            LocalDate lastAmendedAt,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String snapshotVersion,
            List<ArticleResponse> articles
    ) {
        this(
                lawId,
                slug,
                title,
                lawType,
                lawNumber,
                enactedAt,
                lastAmendedAt,
                effectiveFrom,
                effectiveTo,
                snapshotVersion,
                articles,
                null
        );
    }
}
