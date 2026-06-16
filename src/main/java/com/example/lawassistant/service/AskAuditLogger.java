package com.example.lawassistant.service;

import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AskAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(AskAuditLogger.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public void logCompletion(
            String requestId,
            String question,
            QuestionType questionType,
            SearchStatus status,
            String snapshotVersion,
            LocalDate asOf,
            int citedCount,
            int candidateCount,
            double confidence,
            Map<String, Integer> latencyMs,
            Map<String, Integer> retrievalStats
    ) {
        LOGGER.info("event=ask_complete payload={}", completionPayload(
                requestId,
                question,
                questionType,
                status,
                snapshotVersion,
                asOf,
                citedCount,
                candidateCount,
                confidence,
                latencyMs,
                retrievalStats
        ));
    }

    String completionPayload(
            String requestId,
            String question,
            QuestionType questionType,
            SearchStatus status,
            String snapshotVersion,
            LocalDate asOf,
            int citedCount,
            int candidateCount,
            double confidence,
            Map<String, Integer> latencyMs,
            Map<String, Integer> retrievalStats
    ) {
        try {
            return OBJECT_MAPPER.writeValueAsString(completionFields(
                    requestId,
                    question,
                    questionType,
                    status,
                    snapshotVersion,
                    asOf,
                    citedCount,
                    candidateCount,
                    confidence,
                    latencyMs,
                    retrievalStats
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ask completion audit payload", e);
        }
    }

    Map<String, Object> completionFields(
            String requestId,
            String question,
            QuestionType questionType,
            SearchStatus status,
            String snapshotVersion,
            LocalDate asOf,
            int citedCount,
            int candidateCount,
            double confidence,
            Map<String, Integer> latencyMs,
            Map<String, Integer> retrievalStats
    ) {
        String safeQuestion = question == null ? "" : question;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("questionHash", sha256(safeQuestion).substring(0, 12));
        fields.put("questionLength", safeQuestion.length());
        fields.put("questionType", questionType == null ? null : questionType.name());
        fields.put("status", status.name());
        fields.put("snapshotVersion", snapshotVersion);
        fields.put("asOf", asOf == null ? null : asOf.toString());
        fields.put("citedCount", citedCount);
        fields.put("candidateCount", candidateCount);
        fields.put("confidenceBp", (int) Math.round(confidence * 1000));
        fields.put("latencyMs", latencyMs == null ? Map.of() : Map.copyOf(latencyMs));
        fields.put("retrievalStats", retrievalStats == null ? Map.of() : Map.copyOf(retrievalStats));
        return fields;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
