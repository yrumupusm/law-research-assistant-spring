package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.example.lawassistant.infrastructure.llm.ChatMessage;
import com.example.lawassistant.infrastructure.llm.ChatModelClient;
import com.example.lawassistant.infrastructure.openrouter.OpenRouterResponseFormatException;
import com.example.lawassistant.infrastructure.rerank.RerankCandidate;
import com.example.lawassistant.infrastructure.rerank.RerankerClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderSmokeTestServiceTest {

    @Test
    void runReturnsOkStatusForAllProviders() {
        ProviderSmokeTestService service = new ProviderSmokeTestService(
                okChat(),
                okEmbedding(),
                okReranker(),
                "mock",
                "mock",
                "mock"
        );

        var response = service.run();

        assertThat(response.llmStatus()).isEqualTo("ok");
        assertThat(response.embeddingStatus()).isEqualTo("ok");
        assertThat(response.rerankerStatus()).isEqualTo("ok");
        assertThat(response.embeddingDimensions()).isEqualTo(3);
        assertThat(response.rerankedCount()).isEqualTo(1);
        assertThat(response.topRerankedId()).isEqualTo("a");
        assertThat(response.llmError()).isNull();
        assertThat(response.embeddingError()).isNull();
        assertThat(response.rerankerError()).isNull();
    }

    @Test
    void runReportsPartialFailuresWithoutThrowing() {
        ProviderSmokeTestService service = new ProviderSmokeTestService(
                failingChat(new RuntimeException("chat secret failure detail")),
                failingEmbedding(),
                (query, candidates, topK) -> {
                    throw new RuntimeException("reranker unavailable");
                },
                "openrouter",
                "openrouter",
                "cohere"
        );

        var response = service.run();

        assertThat(response.llmStatus()).isEqualTo("failed");
        assertThat(response.embeddingStatus()).isEqualTo("failed");
        assertThat(response.rerankerStatus()).isEqualTo("failed");
        assertThat(response.llmError()).isEqualTo("llm_provider_failed");
        assertThat(response.embeddingError()).isEqualTo("embedding_provider_failed");
        assertThat(response.rerankerError()).isEqualTo("reranker_provider_failed");
        assertThat(response.embeddingDimensions()).isZero();
        assertThat(response.rerankedCount()).isZero();
        assertThat(response.topRerankedId()).isNull();
        assertThat(response.llmResult()).isEmpty();
    }

    @Test
    void runReportsSafeLlmFormatFailureCode() {
        ProviderSmokeTestService service = new ProviderSmokeTestService(
                failingChat(new OpenRouterResponseFormatException(
                        "OpenRouter chat response was not valid JSON.",
                        new IllegalArgumentException("raw text")
                )),
                okEmbedding(),
                okReranker(),
                "openrouter",
                "mock",
                "mock"
        );

        var response = service.run();

        assertThat(response.llmStatus()).isEqualTo("failed");
        assertThat(response.llmError()).isEqualTo("llm_response_format_failed");
        assertThat(response.llmError()).doesNotContain("raw text");
        assertThat(response.embeddingStatus()).isEqualTo("ok");
        assertThat(response.rerankerStatus()).isEqualTo("ok");
    }

    private ChatModelClient okChat() {
        return (messages, schemaName) -> Map.of("status", "OK", "message", "provider ready");
    }

    private ChatModelClient failingChat(RuntimeException exception) {
        return (messages, schemaName) -> {
            throw exception;
        };
    }

    private EmbeddingClient okEmbedding() {
        return new EmbeddingClient() {
            @Override
            public List<Double> embed(String text) {
                return List.of(0.1, 0.2, 0.3);
            }

            @Override
            public List<List<Double>> embedAll(List<String> texts) {
                return texts.stream().map(ignored -> embed("")).toList();
            }
        };
    }

    private EmbeddingClient failingEmbedding() {
        return new EmbeddingClient() {
            @Override
            public List<Double> embed(String text) {
                throw new RuntimeException("embedding unavailable");
            }

            @Override
            public List<List<Double>> embedAll(List<String> texts) {
                throw new RuntimeException("embedding unavailable");
            }
        };
    }

    private RerankerClient okReranker() {
        return (query, candidates, topK) -> List.of(new RerankCandidate("a", "text", 0.9, Map.of()));
    }
}
