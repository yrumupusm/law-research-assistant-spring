package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.IngestionStatus;
import java.time.LocalDateTime;

public record ReindexResponse(
        Long ingestionRunId,
        IngestionStatus status,
        int indexedArticles,
        int failedArticles,
        String errorMessage,
        String snapshotVersion,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
