package com.example.lawassistant.infrastructure.openrouter;

import com.example.lawassistant.infrastructure.llm.ChatMessage;
import com.example.lawassistant.infrastructure.llm.ChatModelClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "openrouter")
public class OpenRouterChatModelClient implements ChatModelClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public OpenRouterChatModelClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.llm.base-url}") String baseUrl,
            @Value("${app.llm.api-key}") String apiKey,
            @Value("${app.llm.model}") String model,
            @Value("${app.llm.temperature:0.1}") double temperature,
            @Value("${app.llm.max-tokens:1200}") int maxTokens,
            @SuppressWarnings("unused")
            @Value("${app.llm.timeout-seconds:30}") int timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = requireApiKey(apiKey);
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("HTTP-Referer", "http://localhost:8080")
                .defaultHeader("X-OpenRouter-Title", "Law Research Assistant Spring")
                .build();
    }

    @Override
    public Map<String, Object> generateJson(List<ChatMessage> messages, String schemaName) {
        List<Map<String, String>> requestMessages = new ArrayList<>();
        requestMessages.add(Map.of(
                "role", "system",
                "content", "스키마 " + schemaName + "에 맞는 유효한 JSON만 반환하세요. 마크다운은 포함하지 말고, 모든 값은 한국어로 작성하세요."
        ));
        for (ChatMessage message : messages) {
            requestMessages.add(Map.of("role", message.role(), "content", message.content()));
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", requestMessages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("response_format", Map.of("type", "json_object"));

        JsonNode response = post("/chat/completions", requestBody);
        String content = response.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new OpenRouterClientException("OpenRouter chat response did not contain message content.");
        }

        try {
            return parseJsonObject(content);
        } catch (JsonProcessingException ex) {
            throw new OpenRouterResponseFormatException(
                    "OpenRouter chat response was not valid JSON for schema " + schemaName + ".",
                    ex
            );
        }
    }

    private Map<String, Object> parseJsonObject(String content) throws JsonProcessingException {
        String candidate = normalizeJsonCandidate(content);
        return objectMapper.readValue(candidate, new TypeReference<>() {
        });
    }

    private String normalizeJsonCandidate(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed
                    .replaceFirst("^```(?:json|JSON)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        String extracted = extractFirstJsonObject(trimmed);
        return extracted.isBlank() ? trimmed : extracted;
    }

    private String extractFirstJsonObject(String value) {
        int start = value.indexOf('{');
        if (start < 0) {
            return "";
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return value.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private JsonNode post(String path, Map<String, Object> requestBody) {
        try {
            return restClient.post()
                    .uri(path)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new OpenRouterClientException("OpenRouter chat request failed.", ex);
        }
    }

    private static String requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenRouterClientException("OpenRouter API key is required when LLM_PROVIDER=openrouter.");
        }
        return apiKey;
    }

}
