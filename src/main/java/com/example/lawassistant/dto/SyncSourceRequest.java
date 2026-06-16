package com.example.lawassistant.dto;

public record SyncSourceRequest(
        String repoUrl,
        String localDir,
        String branch,
        Boolean ingestAfterSync,
        String snapshotPrefix
) {
}
