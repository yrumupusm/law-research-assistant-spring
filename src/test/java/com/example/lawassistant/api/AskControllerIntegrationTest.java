package com.example.lawassistant.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.llm.provider=mock",
        "app.embedding.provider=mock",
        "app.vector.provider=inmemory",
        "app.reranker.provider=mock",
        "app.reference-data.enabled=true"
})
@AutoConfigureMockMvc
class AskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void askReturnsCitationWhenEvidenceExists() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("해외 업체에 기술자료를 제공해도 되나요?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.citedArticles.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.effectiveBasis.sourcePath").value("public-reference-data"))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.cited").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.hydrated").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.weakEvidence").value(0))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.evidenceTopScoreBp").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.diagnostics.requestId").exists());

        mockMvc.perform(get("/api/admin/search-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].status").value("OK"))
                .andExpect(jsonPath("$.items[0].requestId").exists());

        mockMvc.perform(get("/api/admin/agent-traces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.items[*].stepName").value(hasItem("EvidenceValidatorAgent")))
                .andExpect(jsonPath("$.items[*].stepName").value(hasItem("AnswerWriterAndCritic")));
    }

    @Test
    void askRejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid request"));
    }

    @Test
    void askRejectsTooLongQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("가".repeat(4001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid request"));
    }

    @Test
    void askRejectsUnknownRequestFields() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "전략물자 수출 허가 조문을 알려주세요.",
                                  "unknownField": "not allowed"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid request"));
    }

    @Test
    void askRejectsInvalidAsOfDate() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "전략물자 수출 허가 조문을 알려주세요.",
                                  "asOf": "2026-99-99"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid request"));
    }

    @Test
    void searchLogRequestIdCanFilterAgentTraces() throws Exception {
        String askBody = mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("전략물자를 수출하려는데 판정 신청은 어떤 조문을 봐야 하나요?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.diagnostics.requestId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String responseRequestId = objectMapper.readTree(askBody).path("diagnostics").path("requestId").asText();

        String searchLogsBody = mockMvc.perform(get("/api/admin/search-logs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode searchLogs = objectMapper.readTree(searchLogsBody);
        String requestId = searchLogs.path("items").path(0).path("requestId").asText();
        org.assertj.core.api.Assertions.assertThat(requestId).isEqualTo(responseRequestId);

        mockMvc.perform(get("/api/admin/agent-traces").param("requestId", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[*].requestId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo(requestId))))
                .andExpect(jsonPath("$.items[0].stepName").value("QueryAnalyzerAgent"))
                .andExpect(jsonPath("$.items[1].stepName").value("RetrievalAgent"))
                .andExpect(jsonPath("$.items[2].stepName").value("EvidenceValidatorAgent"))
                .andExpect(jsonPath("$.items[3].stepName").value("AnswerWriterAndCritic"));
    }

    @Test
    void v1AskReturnsOriginalSnakeCaseResponseContract() throws Exception {
        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("해외 업체에 기술자료를 제공해도 되나요?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.cited_articles.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.candidate_laws").exists())
                .andExpect(jsonPath("$.candidate_laws[0].law_type").value("law"))
                .andExpect(jsonPath("$.interpretation.question_type").value("confirmatory"))
                .andExpect(jsonPath("$.follow_up_questions").exists())
                .andExpect(jsonPath("$.effective_basis.snapshot_version").value("law-domain-2026-001"))
                .andExpect(jsonPath("$.effective_basis.source_path").value("public-reference-data"))
                .andExpect(jsonPath("$.diagnostics.generated_queries").exists())
                .andExpect(jsonPath("$.diagnostics.retrieval_stats").exists())
                .andExpect(jsonPath("$.citedArticles").doesNotExist())
                .andExpect(jsonPath("$.effectiveBasis").doesNotExist());
    }

    @Test
    void v1AskAcceptsOriginalSnakeCaseAsOfField() throws Exception {
        mockMvc.perform(post("/api/v1/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "전략물자 수출 허가 조문을 알려주세요.",
                                  "as_of": "2025-06-01"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.effective_basis.as_of").value("2025-06-01"));
    }

    @Test
    void askResponseIncludesRequestedAsOfDateInEffectiveBasis() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("전략물자 수출 허가 조문을 알려주세요.", "2025-06-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveBasis.snapshotVersion").value("law-domain-2026-001"))
                .andExpect(jsonPath("$.effectiveBasis.asOf").value("2025-06-01"));

        mockMvc.perform(get("/api/admin/search-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].asOf").value("2025-06-01"));
    }

    @Test
    void askReturnsInsufficientInfoWithoutRetrievalForAmbiguousQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("이거 해도 돼?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_INFO"))
                .andExpect(jsonPath("$.interpretation.questionType").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.citedArticles.length()").value(0))
                .andExpect(jsonPath("$.candidateLaws.length()").value(0))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.retrieved").value(0))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.cited").value(0))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.keywordHits").value(0))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.vectorHits").value(0))
                .andExpect(jsonPath("$.diagnostics.retrievalStats.mergedHits").value(0));
    }

    @Test
    void askReturnsLawMetadataForMetadataQuestion() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("대외무역법 시행일은 언제인가요?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.interpretation.questionType").value("METADATA"))
                .andExpect(jsonPath("$.reasoning").value(org.hamcrest.Matchers.containsString("시행일 2026-01-01")))
                .andExpect(jsonPath("$.citedArticles.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.citedArticles[0].lawTitle").value("대외무역법"));
    }

    @Test
    void askReturnsRevisionComparisonWithPreviousArticleBody() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("대외무역법 제19조의2는 이전과 어떻게 바뀌었나요?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.interpretation.questionType").value("REVISION_COMPARE"))
                .andExpect(jsonPath("$.reasoning").value(org.hamcrest.Matchers.containsString("현행 조문")))
                .andExpect(jsonPath("$.reasoning").value(org.hamcrest.Matchers.containsString("이전 회차")))
                .andExpect(jsonPath("$.reasoning").value(org.hamcrest.Matchers.containsString("2026-01-01")))
                .andExpect(jsonPath("$.reasoning").value(org.hamcrest.Matchers.containsString("2024-01-01")))
                .andExpect(jsonPath("$.citedArticles.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.citedArticles[0].articleNumber").value("제19조의2"))
                .andExpect(jsonPath("$.citedArticles[0].historicalEntries.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.citedArticles[0].historicalEntries[0].effectiveFrom").value("2024-01-01"))
                .andExpect(jsonPath("$.citedArticles[0].historicalEntries[0].content")
                        .value(org.hamcrest.Matchers.containsString("품목 분류와 목적지 국가")));
    }

    private String json(String question) throws Exception {
        return json(question, null);
    }

    private String json(String question, String asOf) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("question", question);
        body.put("asOf", asOf);
        return objectMapper.writeValueAsString(body);
    }
}
