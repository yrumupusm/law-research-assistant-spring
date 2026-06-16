package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lawassistant.domain.entity.SearchLog;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.example.lawassistant.repository.SearchLogRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchLogAgentTest {

    private final SearchLogRepository repository = org.mockito.Mockito.mock(SearchLogRepository.class);
    private final SearchLogAgent agent = new SearchLogAgent(repository);

    @Test
    void previewDoesNotStoreTheFullQuestionEvenWhenQuestionIsShort() {
        SearchLog saved = saveAndCapture("탱크 수출 가능?");

        assertThat(saved.getRequestId()).isEqualTo("request-1");
        assertThat(saved.getQuestionHash()).hasSize(64);
        assertThat(saved.getQuestionLength()).isEqualTo("탱크 수출 가능?".length());
        assertThat(saved.getAsOf()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(saved.getQuestionPreview()).isNotEqualTo("탱크 수출 가능?");
        assertThat(saved.getQuestionPreview()).endsWith("...");
    }

    @Test
    void previewRedactsSensitivePatternsBeforeSaving() {
        String question = "api key=sk-secret-123456 담당자 test@example.com https://internal.example/case 123456789 확인";

        SearchLog saved = saveAndCapture(question);

        assertThat(saved.getQuestionPreview()).contains("[REDACTED]");
        assertThat(saved.getQuestionPreview()).doesNotContain("sk-secret");
        assertThat(saved.getQuestionPreview()).doesNotContain("test@example.com");
        assertThat(saved.getQuestionPreview()).doesNotContain("internal.example");
        assertThat(saved.getQuestionPreview()).doesNotContain("123456789");
    }

    private SearchLog saveAndCapture(String question) {
        when(repository.save(any(SearchLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        agent.save(
                "request-1",
                question,
                QuestionType.CONFIRMATORY,
                SearchStatus.OK,
                0.7,
                "law-domain-2026-001",
                LocalDate.of(2025, 6, 1),
                1,
                1,
                2,
                3
        );

        ArgumentCaptor<SearchLog> captor = ArgumentCaptor.forClass(SearchLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
