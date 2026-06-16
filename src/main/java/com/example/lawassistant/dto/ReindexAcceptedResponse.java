package com.example.lawassistant.dto;

import java.time.LocalDateTime;

public record ReindexAcceptedResponse(
        String status,
        String message,
        Long ingestionRunId,
        String snapshotVersion,
        LocalDateTime startedAt
) {
}
