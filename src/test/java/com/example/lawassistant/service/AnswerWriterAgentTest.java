package com.example.lawassistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.dto.EffectiveBasisDto;
import com.example.lawassistant.dto.QuestionInterpretationDto;
import com.example.lawassistant.infrastructure.llm.ChatMessage;
import com.example.lawassistant.infrastructure.llm.ChatModelClient;
import com.example.lawassistant.infrastructure.openrouter.OpenRouterResponseFormatException;
import com.example.lawassistant.service.model.RetrievalHit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswerWriterAgentTest {

    @Test
    void openRouterDraftFallsBackWhenModelReturnsEnglishDominantText() {
        ChatModelClient englishModel = new EnglishDraftChatModelClient();
        AnswerWriterAgent agent = new AnswerWriterAgent(englishModel, "openrouter");
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-2026-001",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "public-reference-data"
        );
        Law law = new Law("foreign-trade-act", "대외무역법", LawType.LAW, "LAW-001", snapshot);
        Article article = new Article(
                law,
                "제19조",
                "전략물자의 고시 및 수출허가",
                "전략물자를 수출하려는 경우에는 허가 요건과 최종 사용자를 확인해야 한다.",
                1
        );
        QuestionInterpretationDto interpretation = new QuestionInterpretationDto(
                "수출",
                "전략물자",
                List.of("수출통제"),
                List.of("item or data type", "recipient"),
                List.of("전략물자 수출"),
                QuestionType.CONFIRMATORY
        );

        AnswerWriterAgent.DraftAnswer draft = agent.write(
                "전략물자를 수출하려면 무엇을 확인해야 하나요?",
                interpretation,
                List.of(new RetrievalHit(article, 0.9, "키워드 일치")),
                new EffectiveBasisDto("law-domain-2026-001", LocalDateTime.now(), null),
                true
        );

        assertEquals(SearchStatus.OK, draft.status());
        assertFalse(draft.reasoning().contains("The user is asking"));
        assertFalse(draft.followUpQuestions().stream().anyMatch(question -> question.contains("What type")));
        assertEquals("대외무역법 제19조가 현재 질문과 가장 가까운 근거 조문입니다. 실제 판단 전에는 인용 조문과 함께 품목 또는 자료의 성격, 제공받는 기관, 목적지 국가, 제공 목적을 추가로 확인해야 합니다.", draft.reasoning());
    }

    @Test
    void openRouterDraftRetriesWhenFirstResponseViolatesKoreanServicePolicy() {
        SequenceChatModelClient model = new SequenceChatModelClient(List.of(
                Map.of(
                        "reasoning", "This response is based on sample articles for a demo project.",
                        "followUpQuestions", List.of("What type of item is being exported?"),
                        "confidence", 0.91
                ),
                Map.of(
                        "reasoning", "대외무역법 제19조를 근거로 전략물자 수출 여부와 허가 요건을 먼저 확인해야 합니다.",
                        "followUpQuestions", List.of("수출하려는 품목의 구체적인 종류를 확인해 주세요."),
                        "confidence", 91
                )
        ));
        AnswerWriterAgent agent = new AnswerWriterAgent(model, "openrouter");

        AnswerWriterAgent.DraftAnswer draft = agent.write(
                "전략물자를 수출하려면 무엇을 확인해야 하나요?",
                interpretation(),
                List.of(new RetrievalHit(article(), 0.9, "키워드 일치")),
                new EffectiveBasisDto("law-domain-2026-001", LocalDateTime.now(), null),
                true
        );

        assertEquals(2, model.callCount());
        assertEquals(SearchStatus.OK, draft.status());
        assertEquals("대외무역법 제19조를 근거로 전략물자 수출 여부와 허가 요건을 먼저 확인해야 합니다.", draft.reasoning());
        assertEquals(0.91, draft.confidence());
    }

    @Test
    void openRouterDraftRetriesOnceWhenFirstResponseIsNotJson() {
        FormatFailureThenValidChatModelClient model = new FormatFailureThenValidChatModelClient();
        AnswerWriterAgent agent = new AnswerWriterAgent(model, "openrouter");

        AnswerWriterAgent.DraftAnswer draft = agent.write(
                "전략물자를 수출하려면 무엇을 확인해야 하나요?",
                interpretation(),
                List.of(new RetrievalHit(article(), 0.9, "키워드 일치")),
                new EffectiveBasisDto("law-domain-2026-001", LocalDateTime.now(), null),
                true
        );

        assertEquals(2, model.callCount());
        assertEquals(SearchStatus.OK, draft.status());
        assertEquals("대외무역법 제19조를 근거로 허가 요건과 최종 사용자 정보를 확인해야 합니다.", draft.reasoning());
    }

    @Test
    void openRouterDraftPropagatesRepeatedJsonFormatFailure() {
        AlwaysFormatFailureChatModelClient model = new AlwaysFormatFailureChatModelClient();
        AnswerWriterAgent agent = new AnswerWriterAgent(model, "openrouter");

        assertThrows(OpenRouterResponseFormatException.class, () -> agent.write(
                "전략물자를 수출하려면 무엇을 확인해야 하나요?",
                interpretation(),
                List.of(new RetrievalHit(article(), 0.9, "키워드 일치")),
                new EffectiveBasisDto("law-domain-2026-001", LocalDateTime.now(), null),
                true
        ));
        assertEquals(2, model.callCount());
    }

    @Test
    void openRouterDraftDowngradesLowConfidenceButKeepsCitations() {
        SequenceChatModelClient model = new SequenceChatModelClient(List.of(
                Map.of(
                        "reasoning", "제공된 조문과 질문의 직접 관련성은 낮지만, 대외무역법 제19조를 우선 검토할 수 있습니다.",
                        "followUpQuestions", List.of("수출하려는 품목의 구체적인 종류를 확인해 주세요."),
                        "confidence", 0.2
                )
        ));
        AnswerWriterAgent agent = new AnswerWriterAgent(model, "openrouter");

        AnswerWriterAgent.DraftAnswer draft = agent.write(
                "관련 법령이 뭐야?",
                interpretation(),
                List.of(new RetrievalHit(article(), 0.9, "키워드 일치")),
                new EffectiveBasisDto("law-domain-2026-001", LocalDateTime.now(), null),
                true
        );

        assertEquals(SearchStatus.LOW_CONFIDENCE, draft.status());
        assertEquals(1, draft.citedArticles().size());
    }

    @Test
    void weakEvidenceCapsFallbackAnswerAtLowConfidence() {
        AnswerWriterAgent agent = new AnswerWriterAgent(new EnglishDraftChatModelClient(), "mock");

        AnswerWriterAgent.DraftAnswer draft = agent.write(
                "관련 법령이 뭐야?",
                interpretation(),
                List.of(new RetrievalHit(article(), 0.3, "낮은 관련성")),
                new EffectiveBasisDto("law-domain-2026-001", LocalDateTime.now(), null),
                true,
                true
        );

        assertEquals(SearchStatus.LOW_CONFIDENCE, draft.status());
        assertEquals(1, draft.citedArticles().size());
        assertEquals(0.34, draft.confidence());
        assertEquals(true, draft.reasoning().startsWith("검색된 조문은 있으나"));
    }

    @Test
    void metadataQuestionPrefersParentLawWhenSubordinateLawIsRankedFirst() {
        AnswerWriterAgent agent = new AnswerWriterAgent(new EnglishDraftChatModelClient(), "mock");
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-2026-001",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "public-reference-data"
        );
        Law decree = new Law("parent-act-decree", "Parent Act Decree", LawType.ENFORCEMENT_DECREE, "DECREE-001", snapshot);
        decree.applyMetadata(null, null, null, null, null, "{}");
        Law parentLaw = new Law("parent-act", "Parent Act", LawType.LAW, "LAW-001", snapshot);
        parentLaw.applyMetadata(
                java.time.LocalDate.of(2024, 1, 1),
                java.time.LocalDate.of(2025, 1, 1),
                java.time.LocalDate.of(2025, 2, 1),
                null,
                "laws/parent-act.md",
                "{}"
        );
        Article decreeArticle = new Article(decree, "Article 1", "Purpose", "Subordinate law content", 1);
        Article parentArticle = new Article(parentLaw, "Article 1", "Purpose", "Parent law content", 1);
        QuestionInterpretationDto interpretation = new QuestionInterpretationDto(
                "metadata",
                "Parent Act",
                List.of("metadata"),
                List.of("effective date"),
                List.of("Parent Act effective date"),
                QuestionType.METADATA
        );

        AnswerWriterAgent.DraftAnswer draft = agent.write(
                "Parent Act effective date?",
                interpretation,
                List.of(
                        new RetrievalHit(decreeArticle, 0.95, "metadata keyword"),
                        new RetrievalHit(parentArticle, 0.75, "parent law metadata")
                ),
                new EffectiveBasisDto("law-domain-2026-001", LocalDateTime.now(), null),
                true
        );

        assertEquals(SearchStatus.OK, draft.status());
        assertEquals(1, draft.citedArticles().size());
        assertEquals("Parent Act", draft.citedArticles().get(0).lawTitle());
        assertEquals(1, draft.candidateLaws().size());
        assertEquals(LawType.LAW, draft.candidateLaws().get(0).lawType());
    }

    private QuestionInterpretationDto interpretation() {
        return new QuestionInterpretationDto(
                "수출",
                "전략물자",
                List.of("수출통제"),
                List.of("item or data type", "recipient"),
                List.of("전략물자 수출"),
                QuestionType.CONFIRMATORY
        );
    }

    private Article article() {
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-2026-001",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "public-reference-data"
        );
        Law law = new Law("foreign-trade-act", "대외무역법", LawType.LAW, "LAW-001", snapshot);
        return new Article(
                law,
                "제19조",
                "전략물자의 고시 및 수출허가",
                "전략물자를 수출하려는 경우에는 허가 요건과 최종 사용자를 확인해야 한다.",
                1
        );
    }

    private static class EnglishDraftChatModelClient implements ChatModelClient {

        @Override
        public Map<String, Object> generateJson(List<ChatMessage> messages, String schemaName) {
            return Map.of(
                    "reasoning", "The user is asking about export controls and the answer should mention relevant article requirements.",
                    "followUpQuestions", List.of(
                            "What type of item is being exported?",
                            "Who is the recipient or end user?"
                    ),
                    "confidence", 0.91
            );
        }
    }

    private static class SequenceChatModelClient implements ChatModelClient {

        private final List<Map<String, Object>> responses;
        private final List<List<ChatMessage>> calls = new ArrayList<>();

        private SequenceChatModelClient(List<Map<String, Object>> responses) {
            this.responses = responses;
        }

        @Override
        public Map<String, Object> generateJson(List<ChatMessage> messages, String schemaName) {
            calls.add(messages);
            int index = Math.min(calls.size() - 1, responses.size() - 1);
            return responses.get(index);
        }

        private int callCount() {
            return calls.size();
        }
    }

    private static class FormatFailureThenValidChatModelClient implements ChatModelClient {

        private int callCount;

        @Override
        public Map<String, Object> generateJson(List<ChatMessage> messages, String schemaName) {
            callCount++;
            if (callCount == 1) {
                throw new OpenRouterResponseFormatException("not valid JSON", new IllegalArgumentException("text"));
            }
            return Map.of(
                    "reasoning", "대외무역법 제19조를 근거로 허가 요건과 최종 사용자 정보를 확인해야 합니다.",
                    "followUpQuestions", List.of("수출하려는 품목의 구체적인 종류를 확인해 주세요."),
                    "confidence", 0.81
            );
        }

        private int callCount() {
            return callCount;
        }
    }

    private static class AlwaysFormatFailureChatModelClient implements ChatModelClient {

        private int callCount;

        @Override
        public Map<String, Object> generateJson(List<ChatMessage> messages, String schemaName) {
            callCount++;
            throw new OpenRouterResponseFormatException("not valid JSON", new IllegalArgumentException("text"));
        }

        private int callCount() {
            return callCount;
        }
    }
}
