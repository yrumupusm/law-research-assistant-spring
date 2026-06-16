package com.example.lawassistant.infrastructure.rerank;

import java.util.List;

public interface RerankerClient {

    List<RerankCandidate> rerank(String query, List<RerankCandidate> candidates, int topK);
}
