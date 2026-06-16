package com.example.lawassistant.dto;

public record IngestLocalRequest(
        String sourceDir,
        String snapshotPrefix
) {
}
