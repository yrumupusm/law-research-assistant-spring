package com.example.lawassistant.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
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
class EvaluationHarnessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestFactory
    List<DynamicTest> fixedQuestionSet() throws Exception {
        List<EvalQuestion> questions = objectMapper.readValue(
                Files.readString(Path.of("harness/questions.json"), StandardCharsets.UTF_8),
                new TypeReference<>() {
                }
        );

        return questions.stream()
                .map(question -> DynamicTest.dynamicTest(question.id(), () -> {
                    String body = objectMapper.writeValueAsString(new AskPayload(question.question(), null));
                    var result = mockMvc.perform(post("/api/ask")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andExpect(status().isOk())
                            .andReturn();
                    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));

                    assertThat(response.path("status").asText()).isEqualTo(question.expectedStatus());
                    assertThat(response.path("citedArticles").size()).isGreaterThanOrEqualTo(question.minCitedArticles());
                    int vectorHits = response.path("diagnostics").path("retrievalStats").path("vectorHits").asInt(0);
                    if (question.minVectorHits() != null) {
                        assertThat(vectorHits).isGreaterThanOrEqualTo(question.minVectorHits());
                    }
                    if (question.maxVectorHits() != null) {
                        assertThat(vectorHits).isLessThanOrEqualTo(question.maxVectorHits());
                    }
                    if (question.minHistoricalEntries() != null) {
                        assertThat(countHistoricalEntries(response.path("citedArticles")))
                                .as("question=%s expected historicalEntries >= %s, actual citations=%s",
                                        question.id(),
                                        question.minHistoricalEntries(),
                                        describeCitations(response.path("citedArticles")))
                                .isGreaterThanOrEqualTo(question.minHistoricalEntries());
                    }
                    if (!question.expectedCitations().isEmpty()) {
                        assertThat(hasExpectedCitation(response.path("citedArticles"), question.expectedCitations()))
                                .as("question=%s expected one of %s in citedArticles, actual=%s",
                                        question.id(),
                                        question.expectedCitations(),
                                        describeCitations(response.path("citedArticles")))
                                .isTrue();
                    }
                }))
                .toList();
    }

    private boolean hasExpectedCitation(JsonNode citedArticles, List<ExpectedCitation> expectedCitations) {
        for (JsonNode article : citedArticles) {
            String lawTitle = article.path("lawTitle").asText();
            String articleNumber = article.path("articleNumber").asText();
            boolean matched = expectedCitations.stream().anyMatch(expected ->
                    expected.lawTitle().equals(lawTitle) && expected.articleNumber().equals(articleNumber)
            );
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private int countHistoricalEntries(JsonNode citedArticles) {
        int count = 0;
        for (JsonNode article : citedArticles) {
            count += article.path("historicalEntries").size();
        }
        return count;
    }

    private List<String> describeCitations(JsonNode citedArticles) {
        List<String> citations = new java.util.ArrayList<>();
        for (JsonNode article : citedArticles) {
            citations.add(article.path("lawTitle").asText() + " " + article.path("articleNumber").asText());
        }
        return citations;
    }

    record EvalQuestion(
            String id,
            String question,
            String expectedStatus,
            int minCitedArticles,
            Integer minVectorHits,
            Integer maxVectorHits,
            Integer minHistoricalEntries,
            List<ExpectedCitation> expectedCitations
    ) {
    }

    record ExpectedCitation(
            String lawTitle,
            String articleNumber
    ) {
    }

    record AskPayload(
            String question,
            Object asOf
    ) {
    }
}
