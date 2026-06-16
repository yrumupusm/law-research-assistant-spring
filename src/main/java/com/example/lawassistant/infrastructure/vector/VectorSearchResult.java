package com.example.lawassistant.infrastructure.vector;

import java.util.Map;

public record VectorSearchResult(
        String id,
        double score,
        Map<String, Object> metadata
) {
}
