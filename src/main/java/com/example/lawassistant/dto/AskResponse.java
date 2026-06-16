package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.SearchStatus;
import java.util.List;

public record AskResponse(
        SearchStatus status,
        QuestionInterpretationDto interpretation,
        List<CandidateLawDto> candidateLaws,
        List<CitedArticleDto> citedArticles,
        String reasoning,
        List<String> followUpQuestions,
        EffectiveBasisDto effectiveBasis,
        double confidence,
        SearchDiagnosticsDto diagnostics,
        String disclaimer,
        String errorMessage
) {

    public AskResponse(
            SearchStatus status,
            QuestionInterpretationDto interpretation,
            List<CandidateLawDto> candidateLaws,
            List<CitedArticleDto> citedArticles,
            String reasoning,
            List<String> followUpQuestions,
            EffectiveBasisDto effectiveBasis,
            double confidence,
            SearchDiagnosticsDto diagnostics,
            String disclaimer
    ) {
        this(
                status,
                interpretation,
                candidateLaws,
                citedArticles,
                reasoning,
                followUpQuestions,
                effectiveBasis,
                confidence,
                diagnostics,
                disclaimer,
                null
        );
    }

    public AskResponse {
        if (status == SearchStatus.OK && (citedArticles == null || citedArticles.isEmpty())) {
            throw new IllegalArgumentException("status=OK requires at least one cited article");
        }
        if (status == SearchStatus.LOW_CONFIDENCE
                && diagnostics != null
                && diagnostics.retrievalStats() != null
                && retrievedEvidenceCount(diagnostics) > 0
                && (citedArticles == null || citedArticles.isEmpty())) {
            throw new IllegalArgumentException("status=LOW_CONFIDENCE with retrieved evidence requires at least one cited article");
        }
    }

    private static int retrievedEvidenceCount(SearchDiagnosticsDto diagnostics) {
        return Math.max(
                intStat(diagnostics, "retrieved"),
                intStat(diagnostics, "hydrated")
        );
    }

    private static int intStat(SearchDiagnosticsDto diagnostics, String key) {
        Object value = diagnostics.retrievalStats().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
