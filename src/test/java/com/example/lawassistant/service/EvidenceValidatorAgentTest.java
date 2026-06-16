package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.service.model.RetrievalHit;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceValidatorAgentTest {

    private final EvidenceValidatorAgent validator = new EvidenceValidatorAgent();

    @Test
    void insufficientQuestionNeverHasEnoughEvidence() {
        var decision = validator.validate(QuestionType.INSUFFICIENT, List.of(hit(1.0)));

        assertThat(decision.enoughEvidence()).isFalse();
        assertThat(decision.weakEvidence()).isFalse();
    }

    @Test
    void confirmatoryQuestionAcceptsHighScoringEvidence() {
        var decision = validator.validate(QuestionType.CONFIRMATORY, List.of(hit(0.82)));

        assertThat(decision.enoughEvidence()).isTrue();
        assertThat(decision.weakEvidence()).isFalse();
        assertThat(decision.topScore()).isEqualTo(0.82);
    }

    @Test
    void confirmatoryQuestionKeepsLowScoringEvidenceButMarksItWeak() {
        var decision = validator.validate(QuestionType.CONFIRMATORY, List.of(hit(0.4)));

        assertThat(decision.enoughEvidence()).isTrue();
        assertThat(decision.weakEvidence()).isTrue();
        assertThat(decision.reason()).contains("낮습니다");
    }

    @Test
    void emptyHitsAreNotEnoughEvidence() {
        var decision = validator.validate(QuestionType.EXPLORATORY, List.of());

        assertThat(decision.enoughEvidence()).isFalse();
        assertThat(decision.weakEvidence()).isFalse();
    }

    private RetrievalHit hit(double score) {
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        Law law = new Law("law:test", "대외무역법", LawType.LAW, "LAW-TEST", snapshot);
        Article article = new Article(law, "제19조", "전략물자의 고시", "전략물자 수출 검토", 1);
        return new RetrievalHit(article, score, "테스트 근거");
    }
}
