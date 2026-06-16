package com.example.lawassistant.infrastructure.vector;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
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
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.vector.provider", havingValue = "qdrant")
public class QdrantVectorSearchClient implements VectorSearchClient {

    private final RestClient restClient;
    private final String distance;

    public QdrantVectorSearchClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.vector.qdrant.base-url}") String baseUrl,
            @Value("${app.vector.qdrant.api-key:}") String apiKey,
            @Value("${app.vector.qdrant.distance:Cosine}") String distance
    ) {
        RestClient.Builder builder = restClientBuilder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("api-key", apiKey);
        }
        this.restClient = builder.build();
        this.distance = distance;
    }

    @Override
    public void upsert(String collectionName, List<VectorDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        ensureCollection(collectionName, documents.get(0).vector().size());
        List<Map<String, Object>> points = documents.stream()
                .map(document -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("id", parsePointId(document.id()));
                    point.put("vector", document.vector());
                    point.put("payload", document.metadata());
                    return point;
                })
                .toList();
        putOrThrow(
                "/collections/{collectionName}/points?wait=true",
                Map.of("points", points),
                collectionName
        );
    }

    @Override
    public List<VectorSearchResult> search(String collectionName, List<Double> queryVector, int topK) {
        JsonNode response = postOrThrow(
                "/collections/{collectionName}/points/search",
                Map.of(
                        "vector", queryVector,
                        "limit", topK,
                        "with_payload", true
                ),
                collectionName
        );
        JsonNode result = response.path("result");
        if (!result.isArray()) {
            throw new QdrantVectorClientException("Qdrant search response did not contain a result array.");
        }
        List<VectorSearchResult> results = new ArrayList<>();
        for (JsonNode point : result) {
            results.add(new VectorSearchResult(
                    point.path("id").asText(),
                    point.path("score").asDouble(0.0),
                    toPayload(point.path("payload"))
            ));
        }
        return results;
    }

    private void ensureCollection(String collectionName, int dimensions) {
        try {
            restClient.get()
                    .uri("/collections/{collectionName}", collectionName)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                createCollectionIfMissing(collectionName, dimensions);
                return;
            }
            throw new QdrantVectorClientException("Qdrant collection lookup failed: "
                    + collectionName
                    + ", status="
                    + ex.getStatusCode()
                    + ", body="
                    + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ignored) {
            createCollectionIfMissing(collectionName, dimensions);
        }
    }

    private void createCollectionIfMissing(String collectionName, int dimensions) {
        try {
            restClient.put()
                    .uri("/collections/{collectionName}", collectionName)
                    .body(Map.of("vectors", Map.of("size", dimensions, "distance", distance)))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                return;
            }
            throw new QdrantVectorClientException("Qdrant collection creation failed: "
                    + collectionName
                    + ", status="
                    + ex.getStatusCode()
                    + ", body="
                    + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new QdrantVectorClientException("Qdrant collection creation failed: " + collectionName, ex);
        }
    }

    private JsonNode postOrThrow(String uri, Object body, String collectionName) {
        try {
            return restClient.post()
                    .uri(uri, collectionName)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new QdrantVectorClientException("Qdrant request failed for collection: "
                    + collectionName
                    + ", status="
                    + ex.getStatusCode()
                    + ", body="
                    + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new QdrantVectorClientException("Qdrant request failed for collection: " + collectionName, ex);
        }
    }

    private void putOrThrow(String uri, Object body, String collectionName) {
        try {
            restClient.put()
                    .uri(uri, collectionName)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new QdrantVectorClientException("Qdrant PUT request failed for collection: "
                    + collectionName
                    + ", status="
                    + ex.getStatusCode()
                    + ", body="
                    + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new QdrantVectorClientException("Qdrant PUT request failed for collection: " + collectionName, ex);
        }
    }

    private Object parsePointId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return id;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPayload(JsonNode payload) {
        if (!payload.isObject()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        payload.fields().forEachRemaining(entry -> result.put(entry.getKey(), toJavaValue(entry.getValue())));
        return result;
    }

    private Object toJavaValue(JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isNumber()) {
            return value.numberValue();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNull()) {
            return null;
        }
        return value.toString();
    }
}
