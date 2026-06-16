package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.IngestionStatus;
import java.time.LocalDateTime;

public record IngestLocalResponse(
        Long ingestionRunId,
        IngestionStatus status,
        String snapshotVersion,
        int filesProcessed,
        int filesFailed,
        int lawsImported,
        int articlesImported,
        int indexedArticles,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
