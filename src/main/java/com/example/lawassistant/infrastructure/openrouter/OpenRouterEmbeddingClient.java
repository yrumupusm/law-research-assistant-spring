package com.example.lawassistant.infrastructure.openrouter;

import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
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
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "openrouter")
public class OpenRouterEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final String model;
    private final int expectedDimensions;
    private final int maxChars;

    public OpenRouterEmbeddingClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.embedding.base-url}") String baseUrl,
            @Value("${app.embedding.api-key}") String apiKey,
            @Value("${app.embedding.model}") String model,
            @Value("${app.embedding.dimensions}") int expectedDimensions,
            @Value("${app.embedding.max-chars:4000}") int maxChars,
            @SuppressWarnings("unused")
            @Value("${app.llm.timeout-seconds:30}") int timeoutSeconds
    ) {
        this.model = model;
        this.expectedDimensions = expectedDimensions;
        this.maxChars = maxChars;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + requireApiKey(apiKey))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", "http://localhost:8080")
                .defaultHeader("X-OpenRouter-Title", "Law Research Assistant Spring")
                .build();
    }

    @Override
    public List<Double> embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<List<Double>> embedAll(List<String> texts) {
        List<String> safeTexts = texts.stream()
                .map(this::truncate)
                .toList();
        JsonNode response = post(Map.of(
                "model", model,
                "input", safeTexts,
                "encoding_format", "float"
        ));

        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || (texts.size() > 0 && data.isEmpty())) {
            throw new OpenRouterClientException("OpenRouter embedding response did not contain data.");
        }
        List<List<Double>> embeddings = new ArrayList<>();
        int index = 0;
        for (JsonNode item : data) {
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : item.path("embedding")) {
                vector.add(value.asDouble());
            }
            validateDimensions(vector, index);
            embeddings.add(vector);
            index++;
        }
        if (embeddings.size() != texts.size()) {
            throw new OpenRouterClientException("OpenRouter embedding response count did not match input count.");
        }
        return embeddings;
    }

    private String truncate(String text) {
        String value = text == null ? "" : text;
        if (maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private void validateDimensions(List<Double> vector, int index) {
        if (vector.isEmpty()) {
            throw new OpenRouterClientException("OpenRouter embedding response contained an empty vector at index " + index + ".");
        }
        if (expectedDimensions > 0 && vector.size() != expectedDimensions) {
            throw new OpenRouterClientException(
                    "OpenRouter embedding dimension mismatch: expected "
                            + expectedDimensions
                            + ", got "
                            + vector.size()
                            + " at index "
                            + index
                            + "."
            );
        }
    }

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            return restClient.post()
                    .uri("/embeddings")
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new OpenRouterClientException("OpenRouter embedding request failed.", ex);
        }
    }

    private static String requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenRouterClientException("OpenRouter API key is required when EMBEDDING_PROVIDER=openrouter.");
        }
        return apiKey;
    }

}
