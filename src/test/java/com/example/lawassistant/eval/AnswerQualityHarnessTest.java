package com.example.lawassistant.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.domain.enums.SearchStatus;
import com.example.lawassistant.dto.CitedArticleDto;
import com.example.lawassistant.service.AnswerWriterAgent;
import com.example.lawassistant.service.CriticAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class AnswerQualityHarnessTest {

    private static final Pattern ARTICLE_NUMBER_PATTERN = Pattern.compile("제\\s*\\d+조(?:의\\s*\\d+)?");

    private final CriticAgent criticAgent = new CriticAgent();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TestFactory
    List<DynamicTest> answerQualityV2() throws Exception {
        JsonNode root = objectMapper.readTree(
                Files.readString(Path.of("harness/answer-quality-v2.json"), StandardCharsets.UTF_8)
        );

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : root.path("cases")) {
            tests.add(DynamicTest.dynamicTest(testCase.path("id").asText(), () -> {
                JsonNode draftNode = testCase.path("draft");
                JsonNode expectations = testCase.path("expectations");
                var draft = new AnswerWriterAgent.DraftAnswer(
                        SearchStatus.valueOf(draftNode.path("status").asText()),
                        List.of(),
                        citedArticles(draftNode.path("citedArticles")),
                        draftNode.path("reasoning").asText(),
                        stringList(draftNode.path("followUpQuestions")),
                        0.72
                );

                var reviewed = criticAgent.review(draft);
                assertThat(reviewed.status()).isEqualTo(SearchStatus.valueOf(expectations.path("criticStatus").asText()));

                if (expectations.path("qualityChecks").asBoolean(false)) {
                    assertQualityMetrics(draft, expectations);
                }
            }));
        }
        return tests;
    }

    private void assertQualityMetrics(AnswerWriterAgent.DraftAnswer draft, JsonNode expectations) {
        assertThat(draft.citedArticles()).hasSizeGreaterThanOrEqualTo(expectations.path("minCitedArticles").asInt(0));
        assertThat(markdownMarkerCount(draft.reasoning()))
                .isLessThanOrEqualTo(expectations.path("maxMarkdownMarkers").asInt(Integer.MAX_VALUE));

        List<String> visibleTexts = new ArrayList<>();
        visibleTexts.add(draft.reasoning());
        visibleTexts.addAll(draft.followUpQuestions());
        for (JsonNode banned : expectations.path("bannedPhrases")) {
            assertThat(visibleTexts)
                    .noneMatch(text -> text.toLowerCase().contains(banned.asText().toLowerCase()));
        }
        if (expectations.path("requireKoreanVisibleText").asBoolean(false)) {
            assertThat(visibleTexts).allMatch(text -> !isEnglishDominant(text));
        }

        List<String> validArticleNumbers = stringList(expectations.path("validArticleNumbers"));
        if (!validArticleNumbers.isEmpty()) {
            assertThat(draft.citedArticles())
                    .anyMatch(article -> validArticleNumbers.contains(article.articleNumber()));
            assertReasoningArticleNumbersAreAllowed(draft.reasoning(), validArticleNumbers);
        }
    }

    @Test
    void reasoningArticleNumberMetricRejectsNumbersOutsideCitationSet() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                assertReasoningArticleNumbersAreAllowed(
                        "대외무역법 제999조를 우선 검토할 수 있습니다.",
                        List.of("제19조")
                )))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("제999조");
    }

    @Test
    void markdownMarkerMetricCountsBoldPairsNotRawDelimiters() {
        assertThat(markdownMarkerCount("**근거**를 확인하고 **추가 확인**을 요청합니다.")).isEqualTo(2);
    }

    private void assertReasoningArticleNumbersAreAllowed(String reasoning, List<String> validArticleNumbers) {
        List<String> articleNumbers = extractArticleNumbers(reasoning);
        if (articleNumbers.isEmpty()) {
            return;
        }
        assertThat(articleNumbers)
                .as("reasoning must not mention article numbers outside cited/expected articles")
                .allMatch(validArticleNumbers::contains);
    }

    private List<String> extractArticleNumbers(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = ARTICLE_NUMBER_PATTERN.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group().replaceAll("\\s+", ""));
        }
        return result;
    }

    private List<CitedArticleDto> citedArticles(JsonNode nodes) {
        List<CitedArticleDto> result = new ArrayList<>();
        long index = 1L;
        for (JsonNode node : nodes) {
            result.add(new CitedArticleDto(
                    index,
                    index,
                    node.path("lawTitle").asText(),
                    node.path("articleNumber").asText(),
                    "평가용 조문",
                    "평가용 조문 본문",
                    "평가 근거",
                    "hash-" + index,
                    null,
                    null,
                    null,
                    null
            ));
            index++;
        }
        return result;
    }

    private List<String> stringList(JsonNode nodes) {
        List<String> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            result.add(node.asText());
        }
        return result;
    }

    private int markdownMarkerCount(String value) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf("**", index)) >= 0) {
            int end = value.indexOf("**", index + 2);
            if (end < 0) {
                break;
            }
            count++;
            index = end + 2;
        }
        return count;
    }

    private boolean isEnglishDominant(String value) {
        int latin = 0;
        int hangul = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                latin++;
            } else if (ch >= '가' && ch <= '힣') {
                hangul++;
            }
        }
        return latin >= 20 && latin > hangul * 2;
    }
}
