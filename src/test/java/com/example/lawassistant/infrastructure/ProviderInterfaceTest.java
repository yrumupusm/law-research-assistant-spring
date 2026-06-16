package com.example.lawassistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.example.lawassistant.infrastructure.llm.ChatMessage;
import com.example.lawassistant.infrastructure.llm.ChatModelClient;
import com.example.lawassistant.infrastructure.rerank.RerankCandidate;
import com.example.lawassistant.infrastructure.rerank.RerankerClient;
import com.example.lawassistant.infrastructure.vector.VectorDocument;
import com.example.lawassistant.infrastructure.vector.VectorSearchClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "app.llm.provider=mock",
        "app.embedding.provider=mock",
        "app.vector.provider=inmemory",
        "app.reranker.provider=mock"
})
class ProviderInterfaceTest {

    @Autowired
    private ChatModelClient chatModelClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private VectorSearchClient vectorSearchClient;

    @Autowired
    private RerankerClient rerankerClient;

    @Test
    void mockProvidersAreAvailable() {
        var chatResult = chatModelClient.generateJson(
                List.of(new ChatMessage("user", "hello")),
                "TestSchema"
        );
        assertThat(chatResult).containsEntry("schema", "TestSchema");

        var vector = embeddingClient.embed("technical data export");
        assertThat(vector).hasSize(8);

        vectorSearchClient.upsert("test", List.of(new VectorDocument("a1", vector, Map.of("articleId", 1))));
        var vectorHits = vectorSearchClient.search("test", vector, 1);
        assertThat(vectorHits).hasSize(1);
        assertThat(vectorHits.get(0).id()).isEqualTo("a1");

        var reranked = rerankerClient.rerank(
                "technical data",
                List.of(
                        new RerankCandidate("low", "low", 0.1, Map.of()),
                        new RerankCandidate("high", "high", 0.9, Map.of())
                ),
                1
        );
        assertThat(reranked).hasSize(1);
        assertThat(reranked.get(0).id()).isEqualTo("high");
    }
}
