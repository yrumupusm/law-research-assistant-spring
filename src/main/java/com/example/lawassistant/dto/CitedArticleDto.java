package com.example.lawassistant.dto;

import java.time.LocalDate;
import java.util.List;

public record CitedArticleDto(
        Long articleId,
        Long lawId,
        String lawTitle,
        String articleNumber,
        String articleTitle,
        String content,
        String reason,
        String contentHash,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String amendmentKind,
        Long previousArticleId,
        List<ArticleHistoryEntryDto> historicalEntries
) {

    public CitedArticleDto(
            Long articleId,
            Long lawId,
            String lawTitle,
            String articleNumber,
            String articleTitle,
            String content,
            String reason,
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
                reason,
                contentHash,
                effectiveFrom,
                effectiveTo,
                amendmentKind,
                previousArticleId,
                List.of()
        );
    }
}
