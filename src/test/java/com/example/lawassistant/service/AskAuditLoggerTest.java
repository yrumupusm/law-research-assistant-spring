package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AskAuditLoggerTest {

    private final AskAuditLogger logger = new AskAuditLogger();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void completionFieldsDoNotContainQuestionTextOrSensitiveValues() {
        String question = "api key=sk-secret-123456 담당자 test@example.com https://internal.example/case 전략물자 수출 가능?";

        Map<String, Object> fields = logger.completionFields(
                "request-1",
                question,
                QuestionType.CONFIRMATORY,
                SearchStatus.OK,
                "law-domain-2026-001",
                LocalDate.of(2026, 6, 16),
                2,
                3,
                0.82,
                Map.of("analyze", 10, "retrieve", 20, "synthesize", 30),
                Map.of("retrieved", 5, "cited", 2, "vectorHits", 4)
        );

        assertThat(fields).containsEntry("requestId", "request-1");
        assertThat(fields).containsEntry("questionLength", question.length());
        assertThat(fields).containsEntry("questionType", "CONFIRMATORY");
        assertThat(fields).containsEntry("status", "OK");
        assertThat(fields).containsEntry("snapshotVersion", "law-domain-2026-001");
        assertThat(fields).containsEntry("asOf", "2026-06-16");
        assertThat(fields).containsEntry("citedCount", 2);
        assertThat(fields).containsEntry("candidateCount", 3);
        assertThat(fields).containsEntry("confidenceBp", 820);
        assertThat(fields.get("questionHash")).asString().hasSize(12);

        String serializedFields = fields.toString();
        assertThat(serializedFields).doesNotContain("전략물자 수출 가능");
        assertThat(serializedFields).doesNotContain("sk-secret");
        assertThat(serializedFields).doesNotContain("test@example.com");
        assertThat(serializedFields).doesNotContain("internal.example");
    }

    @Test
    void completionFieldsKeepLatencyAndRetrievalStatsForOperations() {
        Map<String, Object> fields = logger.completionFields(
                "request-2",
                "대외무역법 시행일은 언제인가요?",
                QuestionType.METADATA,
                SearchStatus.LOW_CONFIDENCE,
                "law-domain-2026-001",
                null,
                1,
                1,
                0.34,
                Map.of("analyze", 1, "retrieve", 2, "synthesize", 3),
                Map.of("retrieved", 1, "cited", 1, "weakEvidence", 1)
        );

        assertThat(fields.get("latencyMs")).isEqualTo(Map.of("analyze", 1, "retrieve", 2, "synthesize", 3));
        assertThat(fields.get("retrievalStats")).isEqualTo(Map.of("retrieved", 1, "cited", 1, "weakEvidence", 1));
    }

    @Test
    void completionPayloadIsJsonAndDoesNotContainQuestionText() throws Exception {
        String question = "api key=sk-secret-123456 전략물자 수출 가능?";

        String payload = logger.completionPayload(
                "request-3",
                question,
                QuestionType.CONFIRMATORY,
                SearchStatus.OK,
                "law-domain-2026-001",
                null,
                1,
                2,
                0.72,
                Map.of("analyze", 4, "retrieve", 5, "synthesize", 6),
                Map.of("retrieved", 3, "cited", 1)
        );

        var json = objectMapper.readTree(payload);
        assertThat(json.path("requestId").asText()).isEqualTo("request-3");
        assertThat(json.path("questionHash").asText()).hasSize(12);
        assertThat(json.path("questionLength").asInt()).isEqualTo(question.length());
        assertThat(json.path("latencyMs").path("retrieve").asInt()).isEqualTo(5);
        assertThat(json.path("retrievalStats").path("cited").asInt()).isEqualTo(1);
        assertThat(payload).doesNotContain("전략물자 수출 가능");
        assertThat(payload).doesNotContain("sk-secret");
        assertThat(payload).doesNotContain("api key");
    }
}
