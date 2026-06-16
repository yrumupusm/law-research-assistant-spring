package com.example.lawassistant.infrastructure.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.reranker.provider", havingValue = "cohere")
public class CohereRerankerClient implements RerankerClient {

    private final RestClient restClient;
    private final String model;

    public CohereRerankerClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.reranker.base-url}") String baseUrl,
            @Value("${app.reranker.api-key}") String apiKey,
            @Value("${app.reranker.model}") String model
    ) {
        this.model = model;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + requireApiKey(apiKey))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<RerankCandidate> rerank(String query, List<RerankCandidate> candidates, int topK) {
        if (candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        List<String> documents = candidates.stream()
                .map(RerankCandidate::text)
                .toList();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("query", query);
        requestBody.put("documents", documents);
        requestBody.put("top_n", Math.min(topK, candidates.size()));

        JsonNode response = post(requestBody);
        List<RerankCandidate> reranked = new ArrayList<>();
        for (JsonNode result : response.path("results")) {
            int index = result.path("index").asInt(-1);
            if (index < 0 || index >= candidates.size()) {
                continue;
            }
            RerankCandidate original = candidates.get(index);
            reranked.add(new RerankCandidate(
                    original.id(),
                    original.text(),
                    result.path("relevance_score").asDouble(original.score()),
                    original.metadata()
            ));
        }
        if (reranked.isEmpty()) {
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(RerankCandidate::score).reversed())
                    .limit(topK)
                    .toList();
        }
        return reranked;
    }

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            return restClient.post()
                    .uri("/v2/rerank")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new RerankerClientException("Cohere rerank request failed.", ex);
        }
    }

    private static String requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RerankerClientException("Cohere API key is required when RERANKER_PROVIDER=cohere.");
        }
        return apiKey;
    }
}
