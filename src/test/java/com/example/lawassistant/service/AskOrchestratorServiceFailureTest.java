package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.AgentStepStatus;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.dto.QuestionInterpretationDto;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import com.example.lawassistant.service.EvidenceValidatorAgent.EvidenceDecision;
import com.example.lawassistant.service.model.RetrievalHit;
import com.example.lawassistant.service.model.RetrievalResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AskOrchestratorServiceFailureTest {

    @Test
    void missingSnapshotReturnsFailedResponseAndStoresAuditRecords() {
        SnapshotVersionRepository snapshotRepository = mock(SnapshotVersionRepository.class);
        SearchLogAgent searchLogAgent = mock(SearchLogAgent.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        AskOrchestratorService service = new AskOrchestratorService(
                snapshotRepository,
                mock(QueryAnalyzerAgent.class),
                mock(RetrievalAgent.class),
                mock(EvidenceValidatorAgent.class),
                mock(AnswerWriterAgent.class),
                mock(CriticAgent.class),
                searchLogAgent,
                agentTraceService
        );
        when(snapshotRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.empty());

        var response = service.ask("전략물자를 수출해도 되나요?");

        assertThat(response.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("no_snapshot");
        assertThat(response.effectiveBasis().snapshotVersion()).isEqualTo("none");
        assertThat(response.diagnostics().requestId()).isNotBlank();
        assertThat(response.reasoning()).contains("색인된 법령 데이터가 없습니다");
        verify(searchLogAgent).save(
                eq(response.diagnostics().requestId()),
                eq("전략물자를 수출해도 되나요?"),
                eq(QuestionType.INSUFFICIENT),
                eq(SearchStatus.FAILED),
                eq(0.0),
                eq("none"),
                eq(null),
                eq(0),
                eq(0),
                eq(0),
                eq(0)
        );
        verify(agentTraceService).record(
                eq(response.diagnostics().requestId()),
                eq("SnapshotLookup"),
                anyString(),
                eq("no indexed snapshot"),
                eq(AgentStepStatus.FAILED),
                eq(0L),
                eq("색인된 법령 데이터가 없습니다.")
        );
    }

    @Test
    void queryAnalyzerFailureReturnsFailedResponseAndStoresAuditRecords() {
        SnapshotVersionRepository snapshotRepository = mock(SnapshotVersionRepository.class);
        QueryAnalyzerAgent queryAnalyzerAgent = mock(QueryAnalyzerAgent.class);
        SearchLogAgent searchLogAgent = mock(SearchLogAgent.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        AskOrchestratorService service = new AskOrchestratorService(
                snapshotRepository,
                queryAnalyzerAgent,
                mock(RetrievalAgent.class),
                mock(EvidenceValidatorAgent.class),
                mock(AnswerWriterAgent.class),
                mock(CriticAgent.class),
                searchLogAgent,
                agentTraceService
        );
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        String question = "질문 분석이 실패하는 요청";

        when(snapshotRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.of(snapshot));
        when(queryAnalyzerAgent.analyze(question)).thenThrow(new RuntimeException("analyzer unavailable"));

        var response = service.ask(question);

        assertThat(response.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("query_analysis_failed");
        assertThat(response.diagnostics().requestId()).isNotBlank();
        verify(searchLogAgent).save(
                eq(response.diagnostics().requestId()),
                eq(question),
                eq(QuestionType.INSUFFICIENT),
                eq(SearchStatus.FAILED),
                eq(0.0),
                eq("law-domain-test"),
                eq(null),
                eq(0),
                any(Integer.class),
                eq(0),
                eq(0)
        );
        verify(agentTraceService).record(
                eq(response.diagnostics().requestId()),
                eq("QueryAnalyzerAgent"),
                eq("questionLength=" + question.length()),
                eq("failed"),
                eq(AgentStepStatus.FAILED),
                any(Long.class),
                eq("analyzer unavailable")
        );
    }

    @Test
    void criticFailureCodeIsReturnedInAskResponse() {
        SnapshotVersionRepository snapshotRepository = mock(SnapshotVersionRepository.class);
        QueryAnalyzerAgent queryAnalyzerAgent = mock(QueryAnalyzerAgent.class);
        RetrievalAgent retrievalAgent = mock(RetrievalAgent.class);
        EvidenceValidatorAgent evidenceValidatorAgent = mock(EvidenceValidatorAgent.class);
        AnswerWriterAgent answerWriterAgent = mock(AnswerWriterAgent.class);
        SearchLogAgent searchLogAgent = mock(SearchLogAgent.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        AskOrchestratorService service = new AskOrchestratorService(
                snapshotRepository,
                queryAnalyzerAgent,
                retrievalAgent,
                evidenceValidatorAgent,
                answerWriterAgent,
                new CriticAgent(),
                searchLogAgent,
                agentTraceService
        );
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        QuestionInterpretationDto interpretation = new QuestionInterpretationDto(
                "수출",
                "전략물자",
                List.of("수출통제"),
                List.of("목적지 국가"),
                List.of("전략물자 수출"),
                QuestionType.CONFIRMATORY
        );
        RetrievalHit hit = new RetrievalHit(article(), 1.0, "테스트 근거");
        String question = "전략물자를 수출해도 되나요?";

        when(snapshotRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.of(snapshot));
        when(queryAnalyzerAgent.analyze(question)).thenReturn(interpretation);
        when(retrievalAgent.retrieve(interpretation, null)).thenReturn(new RetrievalResult(List.of(hit), 1, 0, 1));
        when(evidenceValidatorAgent.validate(QuestionType.CONFIRMATORY, List.of(hit)))
                .thenReturn(new EvidenceDecision(true, false, 1.0, "충분한 근거"));
        when(answerWriterAgent.write(anyString(), any(), anyList(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(new AnswerWriterAgent.DraftAnswer(
                        SearchStatus.OK,
                        List.of(),
                        List.of(),
                        "근거가 있다고 작성했지만 인용 조문이 없습니다.",
                        List.of(),
                        0.8
                ));

        var response = service.ask(question);

        assertThat(response.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("missing_citation");
        assertThat(response.reasoning()).contains("인용 조문");
        verify(searchLogAgent).save(
                eq(response.diagnostics().requestId()),
                eq(question),
                eq(QuestionType.CONFIRMATORY),
                eq(SearchStatus.FAILED),
                eq(0.0),
                eq("law-domain-test"),
                eq(null),
                eq(0),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class)
        );
    }

    @Test
    void answerWriterFailureReturnsFailedResponseAndStoresAuditRecords() {
        SnapshotVersionRepository snapshotRepository = mock(SnapshotVersionRepository.class);
        QueryAnalyzerAgent queryAnalyzerAgent = mock(QueryAnalyzerAgent.class);
        RetrievalAgent retrievalAgent = mock(RetrievalAgent.class);
        EvidenceValidatorAgent evidenceValidatorAgent = mock(EvidenceValidatorAgent.class);
        AnswerWriterAgent answerWriterAgent = mock(AnswerWriterAgent.class);
        CriticAgent criticAgent = mock(CriticAgent.class);
        SearchLogAgent searchLogAgent = mock(SearchLogAgent.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        AskOrchestratorService service = new AskOrchestratorService(
                snapshotRepository,
                queryAnalyzerAgent,
                retrievalAgent,
                evidenceValidatorAgent,
                answerWriterAgent,
                criticAgent,
                searchLogAgent,
                agentTraceService
        );
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        QuestionInterpretationDto interpretation = new QuestionInterpretationDto(
                "수출",
                "전략물자",
                List.of("수출통제"),
                List.of("목적지 국가"),
                List.of("전략물자 수출"),
                QuestionType.CONFIRMATORY
        );
        RetrievalHit hit = new RetrievalHit(article(), 1.0, "테스트 근거");

        when(snapshotRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.of(snapshot));
        when(queryAnalyzerAgent.analyze("전략물자를 수출해도 되나요?")).thenReturn(interpretation);
        when(retrievalAgent.retrieve(interpretation, null)).thenReturn(new RetrievalResult(List.of(hit), 1, 0, 1));
        when(evidenceValidatorAgent.validate(QuestionType.CONFIRMATORY, List.of(hit)))
                .thenReturn(new EvidenceDecision(true, false, 1.0, "충분한 근거"));
        when(answerWriterAgent.write(anyString(), any(), anyList(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("provider down"));

        var response = service.ask("전략물자를 수출해도 되나요?");

        assertThat(response.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("answer_generation_failed");
        assertThat(response.citedArticles()).isEmpty();
        assertThat(response.diagnostics().requestId()).isNotBlank();
        assertThat(response.reasoning()).contains("오류");

        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(searchLogAgent).save(
                requestIdCaptor.capture(),
                eq("전략물자를 수출해도 되나요?"),
                eq(QuestionType.CONFIRMATORY),
                eq(SearchStatus.FAILED),
                eq(0.0),
                eq("law-domain-test"),
                eq(null),
                eq(0),
                any(Integer.class),
                any(Integer.class),
                any(Integer.class)
        );
        assertThat(requestIdCaptor.getValue()).isEqualTo(response.diagnostics().requestId());
        verify(agentTraceService).record(
                eq(response.diagnostics().requestId()),
                eq("AnswerWriterAndCritic"),
                eq("hits=1"),
                eq("failed"),
                eq(AgentStepStatus.FAILED),
                any(Long.class),
                eq("provider down")
        );
    }

    @Test
    void evidenceValidatorFailureReturnsFailedResponseAndStoresAuditRecords() {
        SnapshotVersionRepository snapshotRepository = mock(SnapshotVersionRepository.class);
        QueryAnalyzerAgent queryAnalyzerAgent = mock(QueryAnalyzerAgent.class);
        RetrievalAgent retrievalAgent = mock(RetrievalAgent.class);
        EvidenceValidatorAgent evidenceValidatorAgent = mock(EvidenceValidatorAgent.class);
        SearchLogAgent searchLogAgent = mock(SearchLogAgent.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        AskOrchestratorService service = new AskOrchestratorService(
                snapshotRepository,
                queryAnalyzerAgent,
                retrievalAgent,
                evidenceValidatorAgent,
                mock(AnswerWriterAgent.class),
                mock(CriticAgent.class),
                searchLogAgent,
                agentTraceService
        );
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        QuestionInterpretationDto interpretation = new QuestionInterpretationDto(
                "수출",
                "전략물자",
                List.of("수출통제"),
                List.of("목적지 국가"),
                List.of("전략물자 수출"),
                QuestionType.CONFIRMATORY
        );
        RetrievalHit hit = new RetrievalHit(article(), 1.0, "테스트 근거");

        when(snapshotRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.of(snapshot));
        when(queryAnalyzerAgent.analyze("전략물자를 수출해도 되나요?")).thenReturn(interpretation);
        when(retrievalAgent.retrieve(interpretation, null)).thenReturn(new RetrievalResult(List.of(hit), 1, 0, 1));
        when(evidenceValidatorAgent.validate(QuestionType.CONFIRMATORY, List.of(hit)))
                .thenThrow(new RuntimeException("validator failed"));

        var response = service.ask("전략물자를 수출해도 되나요?");

        assertThat(response.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("evidence_validation_failed");
        assertThat(response.diagnostics().requestId()).isNotBlank();
        verify(searchLogAgent).save(
                eq(response.diagnostics().requestId()),
                eq("전략물자를 수출해도 되나요?"),
                eq(QuestionType.CONFIRMATORY),
                eq(SearchStatus.FAILED),
                eq(0.0),
                eq("law-domain-test"),
                eq(null),
                eq(0),
                any(Integer.class),
                any(Integer.class),
                eq(0)
        );
        verify(agentTraceService).record(
                eq(response.diagnostics().requestId()),
                eq("EvidenceValidatorAgent"),
                eq("hits=1"),
                eq("failed"),
                eq(AgentStepStatus.FAILED),
                any(Long.class),
                eq("validator failed")
        );
    }

    @Test
    void retrievalFailureReturnsFailedResponseAndStoresAuditRecords() {
        SnapshotVersionRepository snapshotRepository = mock(SnapshotVersionRepository.class);
        QueryAnalyzerAgent queryAnalyzerAgent = mock(QueryAnalyzerAgent.class);
        RetrievalAgent retrievalAgent = mock(RetrievalAgent.class);
        SearchLogAgent searchLogAgent = mock(SearchLogAgent.class);
        AgentTraceService agentTraceService = mock(AgentTraceService.class);
        AskOrchestratorService service = new AskOrchestratorService(
                snapshotRepository,
                queryAnalyzerAgent,
                retrievalAgent,
                mock(EvidenceValidatorAgent.class),
                mock(AnswerWriterAgent.class),
                mock(CriticAgent.class),
                searchLogAgent,
                agentTraceService
        );
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        QuestionInterpretationDto interpretation = new QuestionInterpretationDto(
                "export control",
                "strategic item",
                List.of("export permit"),
                List.of("destination country"),
                List.of("strategic item export"),
                QuestionType.CONFIRMATORY
        );
        String question = "Can I export a strategic item?";

        when(snapshotRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.of(snapshot));
        when(queryAnalyzerAgent.analyze(question)).thenReturn(interpretation);
        when(retrievalAgent.retrieve(interpretation, null)).thenThrow(new RuntimeException("vector provider down"));

        var response = service.ask(question);

        assertThat(response.status()).isEqualTo(SearchStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("retrieval_failed");
        assertThat(response.citedArticles()).isEmpty();
        assertThat(response.diagnostics().requestId()).isNotBlank();
        verify(searchLogAgent).save(
                eq(response.diagnostics().requestId()),
                eq(question),
                eq(QuestionType.CONFIRMATORY),
                eq(SearchStatus.FAILED),
                eq(0.0),
                eq("law-domain-test"),
                eq(null),
                eq(0),
                any(Integer.class),
                any(Integer.class),
                eq(0)
        );
        verify(agentTraceService).record(
                eq(response.diagnostics().requestId()),
                eq("RetrievalAgent"),
                eq("queries=" + interpretation.generatedQueries().size()),
                eq("failed"),
                eq(AgentStepStatus.FAILED),
                any(Long.class),
                eq("vector provider down")
        );
    }

    private Article article() {
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        Law law = new Law("law:test", "대외무역법", LawType.LAW, "LAW-TEST", snapshot);
        Article article = new Article(law, "제19조의2", "전략물자 수출허가", "전략물자 수출 검토 조문입니다.", 1);
        ReflectionTestUtils.setField(law, "id", 1L);
        ReflectionTestUtils.setField(article, "id", 10L);
        return article;
    }
}
