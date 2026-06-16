package com.example.lawassistant.dto;

public record SyncSourceResponse(
        String action,
        String repoUrl,
        String localDir,
        String branch,
        String commitHash,
        IngestLocalResponse ingestion
) {
}
