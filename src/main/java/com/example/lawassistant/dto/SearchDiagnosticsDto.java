package com.example.lawassistant.dto;

import java.util.List;
import java.util.Map;

public record SearchDiagnosticsDto(
        String requestId,
        List<String> generatedQueries,
        Map<String, Integer> retrievalStats,
        Map<String, Integer> latencyMs
) {
}
