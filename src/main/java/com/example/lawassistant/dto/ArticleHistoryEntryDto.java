package com.example.lawassistant.dto;

import java.time.LocalDate;

public record ArticleHistoryEntryDto(
        Long articleId,
        String articleNumber,
        String articleTitle,
        String content,
        String contentHash,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String amendmentKind,
        Long previousArticleId,
        boolean current
) {
}
