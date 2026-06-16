package com.example.lawassistant.dto;

import java.util.Map;

public record ProviderSmokeTestResponse(
        String llmProvider,
        String embeddingProvider,
        String rerankerProvider,
        String llmStatus,
        String embeddingStatus,
        String rerankerStatus,
        int embeddingDimensions,
        int rerankedCount,
        String topRerankedId,
        Map<String, Object> llmResult,
        String llmError,
        String embeddingError,
        String rerankerError
) {
    public ProviderSmokeTestResponse(
            String llmProvider,
            String embeddingProvider,
            String rerankerProvider,
            String llmStatus,
            String embeddingStatus,
            String rerankerStatus,
            int embeddingDimensions,
            int rerankedCount,
            String topRerankedId,
            Map<String, Object> llmResult
    ) {
        this(
                llmProvider,
                embeddingProvider,
                rerankerProvider,
                llmStatus,
                embeddingStatus,
                rerankerStatus,
                embeddingDimensions,
                rerankedCount,
                topRerankedId,
                llmResult,
                null,
                null,
                null
        );
    }
}
