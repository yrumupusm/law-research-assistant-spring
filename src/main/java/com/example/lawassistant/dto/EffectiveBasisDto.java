package com.example.lawassistant.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EffectiveBasisDto(
        String snapshotVersion,
        LocalDateTime indexedAt,
        String sourcePath,
        LocalDate asOf
) {

    public EffectiveBasisDto(String snapshotVersion, LocalDateTime indexedAt, LocalDate asOf) {
        this(snapshotVersion, indexedAt, null, asOf);
    }
}
