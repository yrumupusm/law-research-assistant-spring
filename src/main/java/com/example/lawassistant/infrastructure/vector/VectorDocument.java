package com.example.lawassistant.infrastructure.vector;

import java.util.List;
import java.util.Map;

public record VectorDocument(
        String id,
        List<Double> vector,
        Map<String, Object> metadata
) {
}
