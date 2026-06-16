package com.example.lawassistant.service;

import com.example.lawassistant.domain.enums.SearchStatus;
import org.springframework.stereotype.Service;

@Service
public class CriticAgent {

    public AnswerWriterAgent.DraftAnswer review(AnswerWriterAgent.DraftAnswer draft) {
        if ((draft.status() == SearchStatus.OK || draft.status() == SearchStatus.LOW_CONFIDENCE)
                && draft.citedArticles().isEmpty()) {
            return new AnswerWriterAgent.DraftAnswer(
                    SearchStatus.FAILED,
                    draft.candidateLaws(),
                    draft.citedArticles(),
                    "인용 조문이 없는 답변은 허용되지 않습니다.",
                    draft.followUpQuestions(),
                    0.0,
                    "missing_citation"
            );
        }
        if (violatesVisibleTextPolicy(draft.reasoning())
                || draft.followUpQuestions().stream().anyMatch(this::violatesVisibleTextPolicy)) {
            return new AnswerWriterAgent.DraftAnswer(
                    SearchStatus.FAILED,
                    draft.candidateLaws(),
                    draft.citedArticles(),
                    "응답 품질 기준을 충족하지 못했습니다. 질문을 다시 시도해 주세요.",
                    draft.followUpQuestions(),
                    0.0,
                    "response_quality_failed"
            );
        }
        return draft;
    }

    private boolean violatesVisibleTextPolicy(String value) {
        return containsForbiddenCopy(value) || containsDecisiveLegalPhrase(value) || isEnglishDominant(value);
    }

    private boolean containsForbiddenCopy(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("demo")
                || lower.contains("sample")
                || lower.contains("mini project")
                || lower.contains("this response")
                || value.contains("데모")
                || value.contains("샘플")
                || value.contains("미니 프로젝트")
                || value.contains("실제 회사 내부 데이터");
    }

    private boolean containsDecisiveLegalPhrase(String value) {
        if (value == null) {
            return false;
        }
        return value.contains("위법입니다")
                || value.contains("합법입니다")
                || value.contains("문제없습니다")
                || value.contains("허가됩니다")
                || value.contains("불허됩니다")
                || value.contains("반드시 위반");
    }

    private boolean isEnglishDominant(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
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
