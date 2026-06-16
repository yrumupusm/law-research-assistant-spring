package com.example.lawassistant.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.lawassistant.domain.enums.SearchStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AskResponseInvariantTest {

    @Test
    void okStatusRequiresCitedArticles() {
        assertThatThrownBy(() -> new AskResponse(
                SearchStatus.OK,
                null,
                List.of(),
                List.of(),
                "reasoning",
                List.of(),
                new EffectiveBasisDto("law-domain-test", LocalDateTime.now(), null),
                0.8,
                new SearchDiagnosticsDto("request-1", List.of(), Map.of(), Map.of()),
                "disclaimer"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lowConfidenceWithRetrievedEvidenceRequiresCitedArticles() {
        assertThatThrownBy(() -> new AskResponse(
                SearchStatus.LOW_CONFIDENCE,
                null,
                List.of(),
                List.of(),
                "reasoning",
                List.of(),
                new EffectiveBasisDto("law-domain-test", LocalDateTime.now(), null),
                0.2,
                new SearchDiagnosticsDto("request-1", List.of(), Map.of("retrieved", 3), Map.of()),
                "disclaimer"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lowConfidenceWithHydratedEvidenceRequiresCitedArticles() {
        assertThatThrownBy(() -> new AskResponse(
                SearchStatus.LOW_CONFIDENCE,
                null,
                List.of(),
                List.of(),
                "reasoning",
                List.of(),
                new EffectiveBasisDto("law-domain-test", LocalDateTime.now(), null),
                0.2,
                new SearchDiagnosticsDto("request-1", List.of(), Map.of("hydrated", 3), Map.of()),
                "disclaimer"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lowConfidenceWithoutRetrievedEvidenceCanAskForMoreInformation() {
        var response = new AskResponse(
                SearchStatus.LOW_CONFIDENCE,
                null,
                List.of(),
                List.of(),
                "관련 근거를 충분히 찾지 못했습니다.",
                List.of("질문 범위를 더 구체적으로 알려주세요."),
                new EffectiveBasisDto("law-domain-test", LocalDateTime.now(), null),
                0.2,
                new SearchDiagnosticsDto("request-1", List.of(), Map.of("retrieved", 0), Map.of()),
                "disclaimer"
        );

        org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(SearchStatus.LOW_CONFIDENCE);
    }
}
