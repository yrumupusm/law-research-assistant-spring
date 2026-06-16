package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.IngestionStatus;
import java.time.LocalDateTime;

public record IngestionRunItemDto(
        Long ingestionRunId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        IngestionStatus status,
        Integer filesProcessed,
        Integer filesFailed,
        String snapshotVersion,
        String errorMessage
) {
}
