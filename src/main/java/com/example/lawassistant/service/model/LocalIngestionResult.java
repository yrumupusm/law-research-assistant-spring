package com.example.lawassistant.service.model;

import com.example.lawassistant.domain.entity.IngestionRun;

public record LocalIngestionResult(
        IngestionRun run,
        String snapshotVersion,
        int filesProcessed,
        int filesFailed,
        int lawsImported,
        int articlesImported,
        int indexedArticles
) {
}
