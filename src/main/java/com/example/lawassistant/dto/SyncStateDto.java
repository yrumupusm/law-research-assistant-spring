package com.example.lawassistant.dto;

import java.time.LocalDateTime;

public record SyncStateDto(
        String lastSyncedCommitSha,
        LocalDateTime lastSyncAt,
        LocalDateTime lastForcePushDetectedAt
) {
}
