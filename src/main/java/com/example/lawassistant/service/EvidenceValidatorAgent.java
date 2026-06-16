package com.example.lawassistant.service;

import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.service.model.RetrievalHit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvidenceValidatorAgent {

    private static final double EXPLORATORY_THRESHOLD = 0.55;
    private static final double CONFIRMATORY_THRESHOLD = 0.75;
    private static final double STRUCTURED_THRESHOLD = 0.50;

    public boolean hasEnoughEvidence(QuestionType questionType, List<RetrievalHit> hits) {
        return validate(questionType, hits).enoughEvidence();
    }

    public EvidenceDecision validate(QuestionType questionType, List<RetrievalHit> hits) {
        if (questionType == QuestionType.INSUFFICIENT) {
            return new EvidenceDecision(false, false, 0.0, "질문 정보가 부족합니다.");
        }
        if (hits == null || hits.isEmpty()) {
            return new EvidenceDecision(false, false, 0.0, "검색된 조문이 없습니다.");
        }

        double topScore = hits.stream()
                .mapToDouble(RetrievalHit::score)
                .max()
                .orElse(0.0);
        double threshold = threshold(questionType);
        if (topScore >= threshold) {
            return new EvidenceDecision(true, false, topScore, "검색 점수가 기준을 충족했습니다.");
        }
        return new EvidenceDecision(true, true, topScore, "검색된 조문은 있으나 관련성 점수가 낮습니다.");
    }

    private double threshold(QuestionType questionType) {
        return switch (questionType) {
            case EXPLORATORY -> EXPLORATORY_THRESHOLD;
            case METADATA, REVISION_COMPARE -> STRUCTURED_THRESHOLD;
            case CONFIRMATORY -> CONFIRMATORY_THRESHOLD;
            case INSUFFICIENT -> Double.POSITIVE_INFINITY;
        };
    }

    public record EvidenceDecision(
            boolean enoughEvidence,
            boolean weakEvidence,
            double topScore,
            String reason
    ) {
    }
}
