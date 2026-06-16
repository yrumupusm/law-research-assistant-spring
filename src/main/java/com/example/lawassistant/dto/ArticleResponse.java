package com.example.lawassistant.dto;

import java.time.LocalDate;

public record ArticleResponse(
        Long articleId,
        Long lawId,
        String lawTitle,
        String articleNumber,
        String articleTitle,
        String content,
        String contentHash,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String amendmentKind,
        Long previousArticleId,
        EffectiveBasisDto effectiveBasis
) {
    public ArticleResponse(
            Long articleId,
            Long lawId,
            String lawTitle,
            String articleNumber,
            String articleTitle,
            String content,
            String contentHash,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String amendmentKind,
            Long previousArticleId
    ) {
        this(
                articleId,
                lawId,
                lawTitle,
                articleNumber,
                articleTitle,
                content,
                contentHash,
                effectiveFrom,
                effectiveTo,
                amendmentKind,
                previousArticleId,
                null
        );
    }
}
