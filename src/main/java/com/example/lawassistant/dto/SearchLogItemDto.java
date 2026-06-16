package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SearchLogItemDto(
        Long id,
        String requestId,
        String questionHash,
        String questionPreview,
        Integer questionLength,
        QuestionType questionType,
        SearchStatus status,
        Double confidence,
        String snapshotVersion,
        LocalDate asOf,
        Integer citedArticleCount,
        Integer latencyAnalyzeMs,
        Integer latencyRetrieveMs,
        Integer latencySynthesizeMs,
        LocalDateTime createdAt
) {
}
