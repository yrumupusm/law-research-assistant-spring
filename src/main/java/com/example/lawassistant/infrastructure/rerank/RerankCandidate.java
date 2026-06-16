package com.example.lawassistant.infrastructure.rerank;

import java.util.Map;

public record RerankCandidate(
        String id,
        String text,
        double score,
        Map<String, Object> metadata
) {
}
