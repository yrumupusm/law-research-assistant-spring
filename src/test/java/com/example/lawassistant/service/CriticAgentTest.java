package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.domain.enums.SearchStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class CriticAgentTest {

    private final CriticAgent criticAgent = new CriticAgent();

    @Test
    void failsLowConfidenceAnswerWithoutCitations() {
        var reviewed = criticAgent.review(new AnswerWriterAgent.DraftAnswer(
                SearchStatus.LOW_CONFIDENCE,
                List.of(),
                List.of(),
                "관련성이 낮지만 참고할 수 있습니다.",
                List.of(),
                0.2
        ));

        assertThat(reviewed.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(reviewed.reasoning()).contains("인용 조문");
        assertThat(reviewed.errorMessage()).isEqualTo("missing_citation");
    }

    @Test
    void failsAnswerContainingPortfolioCopy() {
        var reviewed = criticAgent.review(new AnswerWriterAgent.DraftAnswer(
                SearchStatus.OK,
                List.of(),
                List.of(new com.example.lawassistant.dto.CitedArticleDto(
                        1L,
                        1L,
                        "대외무역법",
                        "제19조",
                        "전략물자의 고시 및 수출통제",
                        "전략물자 관련 내용",
                        "테스트 근거",
                        "hash",
                        null,
                        null,
                        null,
                        null
                )),
                "This response is based on sample articles for a demo project.",
                List.of(),
                0.8
        ));

        assertThat(reviewed.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(reviewed.confidence()).isZero();
        assertThat(reviewed.errorMessage()).isEqualTo("response_quality_failed");
    }

    @Test
    void failsWhenFollowUpContainsEnglishDominantText() {
        var reviewed = criticAgent.review(validDraft(
                "대외무역법 제19조를 근거로 품목과 최종 사용자를 확인해야 합니다.",
                List.of("What type of item is being exported and who is the end user?")
        ));

        assertThat(reviewed.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(reviewed.reasoning()).contains("응답 품질 기준");
        assertThat(reviewed.errorMessage()).isEqualTo("response_quality_failed");
    }

    @Test
    void failsDecisiveLegalConclusion() {
        var reviewed = criticAgent.review(validDraft(
                "대외무역법 제19조에 따라 이 거래는 문제없습니다.",
                List.of("품목과 목적지 국가를 확인해 주세요.")
        ));

        assertThat(reviewed.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(reviewed.confidence()).isZero();
        assertThat(reviewed.errorMessage()).isEqualTo("response_quality_failed");
    }

    private AnswerWriterAgent.DraftAnswer validDraft(String reasoning, List<String> followUps) {
        return new AnswerWriterAgent.DraftAnswer(
                SearchStatus.OK,
                List.of(),
                List.of(new com.example.lawassistant.dto.CitedArticleDto(
                        1L,
                        1L,
                        "대외무역법",
                        "제19조",
                        "전략물자의 고시 및 수출통제",
                        "전략물자 관련 내용",
                        "테스트 근거",
                        "hash",
                        null,
                        null,
                        null,
                        null
                )),
                reasoning,
                followUps,
                0.8
        );
    }
}
