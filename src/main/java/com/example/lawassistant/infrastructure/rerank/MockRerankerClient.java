package com.example.lawassistant.infrastructure.rerank;

import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.reranker.provider", havingValue = "mock", matchIfMissing = true)
public class MockRerankerClient implements RerankerClient {

    @Override
    public List<RerankCandidate> rerank(String query, List<RerankCandidate> candidates, int topK) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(RerankCandidate::score).reversed())
                .limit(topK)
                .toList();
    }
}
