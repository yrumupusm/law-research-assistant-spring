package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.domain.enums.QuestionType;
import org.junit.jupiter.api.Test;

class QueryAnalyzerAgentTest {

    private final QueryAnalyzerAgent agent = new QueryAnalyzerAgent();

    @Test
    void classifiesRevisionContentQuestionAsRevisionCompare() {
        var result = agent.analyze("대외무역법 제19조의2 개정 내용은 무엇이 바뀌었나요?");

        assertThat(result.questionType()).isEqualTo(QuestionType.REVISION_COMPARE);
        assertThat(result.generatedQueries()).contains("대외무역법", "제19조의2");
    }

    @Test
    void classifiesRevisionDateQuestionAsMetadata() {
        var result = agent.analyze("대외무역법 개정일은 언제인가요?");

        assertThat(result.questionType()).isEqualTo(QuestionType.METADATA);
        assertThat(result.generatedQueries()).contains("대외무역법");
    }

    @Test
    void expandsSpacedLawTitleToCanonicalSearchQuery() {
        var result = agent.analyze("대외 무역법 제19조의2 수출허가 기준을 알려주세요.");

        assertThat(result.object()).isEqualTo("대외무역법");
        assertThat(result.generatedQueries()).contains("대외무역법", "제19조의2");
    }

    @Test
    void expandsCommonLawAliasesToCanonicalSearchQuery() {
        var result = agent.analyze("산업기술보호법상 국가핵심기술 수출은 어떤 조문을 봐야 하나요?");

        assertThat(result.object()).isEqualTo("산업기술의 유출방지 및 보호에 관한 법률");
        assertThat(result.generatedQueries()).contains("산업기술의 유출방지 및 보호에 관한 법률");
    }

    @Test
    void doesNotTreatTechnologyTransferAsRevisionCompare() {
        var result = agent.analyze("방산 관련 기술자료를 해외로 이전하려면 어떤 절차가 필요한가요?");

        assertThat(result.questionType()).isEqualTo(QuestionType.CONFIRMATORY);
        assertThat(result.action()).isEqualTo("이전");
        assertThat(result.domainCandidates()).contains("방산", "기술이전");
    }
}
