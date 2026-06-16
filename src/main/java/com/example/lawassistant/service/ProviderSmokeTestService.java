package com.example.lawassistant.service;

import com.example.lawassistant.dto.ProviderSmokeTestResponse;
import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.example.lawassistant.infrastructure.llm.ChatMessage;
import com.example.lawassistant.infrastructure.llm.ChatModelClient;
import com.example.lawassistant.infrastructure.openrouter.OpenRouterResponseFormatException;
import com.example.lawassistant.infrastructure.rerank.RerankCandidate;
import com.example.lawassistant.infrastructure.rerank.RerankerClient;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProviderSmokeTestService {

    private final ChatModelClient chatModelClient;
    private final EmbeddingClient embeddingClient;
    private final RerankerClient rerankerClient;
    private final String llmProvider;
    private final String embeddingProvider;
    private final String rerankerProvider;

    public ProviderSmokeTestService(
            ChatModelClient chatModelClient,
            EmbeddingClient embeddingClient,
            RerankerClient rerankerClient,
            @Value("${app.llm.provider}") String llmProvider,
            @Value("${app.embedding.provider}") String embeddingProvider,
            @Value("${app.reranker.provider}") String rerankerProvider
    ) {
        this.chatModelClient = chatModelClient;
        this.embeddingClient = embeddingClient;
        this.rerankerClient = rerankerClient;
        this.llmProvider = llmProvider;
        this.embeddingProvider = embeddingProvider;
        this.rerankerProvider = rerankerProvider;
    }

    public ProviderSmokeTestResponse run() {
        String llmStatus = "ok";
        String embeddingStatus = "ok";
        String rerankerStatus = "ok";
        String llmError = null;
        String embeddingError = null;
        String rerankerError = null;
        Map<String, Object> llmResult = Map.of();
        int embeddingDimensions = 0;
        int rerankedCount = 0;
        String topRerankedId = null;

        try {
            llmResult = chatModelClient.generateJson(
                    List.of(new ChatMessage("user", "Return {\"status\":\"OK\",\"message\":\"provider ready\"}.")),
                    "ProviderSmokeTest"
            );
        } catch (RuntimeException ex) {
            llmStatus = "failed";
            llmError = providerErrorCode("llm", ex);
        }

        try {
            embeddingDimensions = embeddingClient.embed("provider smoke test").size();
        } catch (RuntimeException ex) {
            embeddingStatus = "failed";
            embeddingError = providerErrorCode("embedding", ex);
        }

        try {
            var reranked = rerankerClient.rerank(
                    "무역안보관리원 법적 근거",
                    List.of(
                            new RerankCandidate("a", "대외무역법 제5조 무역안보관리원 근거", 0.5, Map.of()),
                            new RerankCandidate("b", "방위사업법 제57조 방산물자 수출", 0.4, Map.of())
                    ),
                    1
            );
            rerankedCount = reranked.size();
            topRerankedId = reranked.isEmpty() ? null : reranked.get(0).id();
        } catch (RuntimeException ex) {
            rerankerStatus = "failed";
            rerankerError = providerErrorCode("reranker", ex);
        }

        return new ProviderSmokeTestResponse(
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
                llmError,
                embeddingError,
                rerankerError
        );
    }

    private String providerErrorCode(String providerRole, RuntimeException ex) {
        if ("llm".equals(providerRole) && ex instanceof OpenRouterResponseFormatException) {
            return "llm_response_format_failed";
        }
        return providerRole + "_provider_failed";
    }
}
