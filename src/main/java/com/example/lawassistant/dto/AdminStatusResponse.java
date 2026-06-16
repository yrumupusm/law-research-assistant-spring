package com.example.lawassistant.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminStatusResponse(
        String lastSnapshotVersion,
        LocalDateTime lastIndexedAt,
        String indexStatus,
        long lawsCount,
        long articlesCount,
        long indexedArticlesCount,
        long unindexedArticlesCount,
        long searchLogCount,
        List<IngestionRunItemDto> recentFailures,
        boolean reindexEnabled,
        SyncStateDto syncState
) {
}
