package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.AgentStepStatus;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.ResearchArea;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.dto.AskResponse;
import com.example.lawassistant.dto.EffectiveBasisDto;
import com.example.lawassistant.dto.QuestionInterpretationDto;
import com.example.lawassistant.dto.SearchDiagnosticsDto;
import com.example.lawassistant.service.EvidenceValidatorAgent.EvidenceDecision;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import com.example.lawassistant.service.AnswerWriterAgent.DraftAnswer;
import com.example.lawassistant.service.model.RetrievalHit;
import com.example.lawassistant.service.model.RetrievalResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AskOrchestratorService {

    private static final String DEFAULT_DISCLAIMER = "이 답변은 법령 조사 보조 결과이며, 최종 법률 판단을 대체하지 않습니다.";

    private final SnapshotVersionRepository snapshotVersionRepository;
    private final QueryAnalyzerAgent queryAnalyzerAgent;
    private final RetrievalAgent retrievalAgent;
    private final EvidenceValidatorAgent evidenceValidatorAgent;
    private final AnswerWriterAgent answerWriterAgent;
    private final CriticAgent criticAgent;
    private final SearchLogAgent searchLogAgent;
    private final AgentTraceService agentTraceService;
    private final AskAuditLogger askAuditLogger;

    public AskOrchestratorService(
            SnapshotVersionRepository snapshotVersionRepository,
            QueryAnalyzerAgent queryAnalyzerAgent,
            RetrievalAgent retrievalAgent,
            EvidenceValidatorAgent evidenceValidatorAgent,
            AnswerWriterAgent answerWriterAgent,
            CriticAgent criticAgent,
            SearchLogAgent searchLogAgent,
            AgentTraceService agentTraceService
    ) {
        this.snapshotVersionRepository = snapshotVersionRepository;
        this.queryAnalyzerAgent = queryAnalyzerAgent;
        this.retrievalAgent = retrievalAgent;
        this.evidenceValidatorAgent = evidenceValidatorAgent;
        this.answerWriterAgent = answerWriterAgent;
        this.criticAgent = criticAgent;
        this.searchLogAgent = searchLogAgent;
        this.agentTraceService = agentTraceService;
        this.askAuditLogger = new AskAuditLogger();
    }

    @Transactional
    public AskResponse ask(String question) {
        return ask(question, null);
    }

    @Transactional
    public AskResponse ask(String question, LocalDate asOf) {
        return ask(question, asOf, List.of());
    }

    @Transactional
    public AskResponse ask(String question, LocalDate asOf, List<ResearchArea> researchAreas) {
        List<ResearchArea> safeResearchAreas = researchAreas == null ? List.of() : List.copyOf(researchAreas);
        String disclaimer = disclaimerFor(safeResearchAreas);
        String researchAreasTrace = traceResearchAreas(safeResearchAreas);
        String requestId = UUID.randomUUID().toString();
        Map<String, Integer> latency = new LinkedHashMap<>();
        SnapshotVersion snapshot = snapshotVersionRepository
                .findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED)
                .orElse(null);

        if (snapshot == null) {
            agentTraceService.record(
                    requestId,
                    "SnapshotLookup",
                    "questionLength=" + question.length(),
                    "no indexed snapshot",
                    AgentStepStatus.FAILED,
                    0,
                    "색인된 법령 데이터가 없습니다."
            );
            searchLogAgent.save(
                    requestId,
                    question,
                    QuestionType.INSUFFICIENT,
                    SearchStatus.FAILED,
                    0.0,
                    "none",
                    asOf,
                    0,
                    0,
                    0,
                    0
            );
            askAuditLogger.logCompletion(
                    requestId,
                    question,
                    QuestionType.INSUFFICIENT,
                    SearchStatus.FAILED,
                    "none",
                    asOf,
                    0,
                    0,
                    0.0,
                    Map.of("analyze", 0, "retrieve", 0, "synthesize", 0),
                    Map.of("retrieved", 0, "cited", 0)
            );
            EffectiveBasisDto emptyBasis = new EffectiveBasisDto("none", LocalDateTime.MIN, null, asOf);
            return new AskResponse(
                    SearchStatus.FAILED,
                    null,
                    List.of(),
                    List.of(),
                    "색인된 법령 데이터가 없습니다.",
                    List.of(),
                    emptyBasis,
                    0.0,
                    new SearchDiagnosticsDto(requestId, List.of(), Map.of(), Map.of()),
                    disclaimer,
                    "no_snapshot"
            );
        }

        long start = System.nanoTime();
        QuestionInterpretationDto interpretation;
        try {
            interpretation = queryAnalyzerAgent.analyze(question);
        } catch (RuntimeException ex) {
            int analyzeMs = elapsedMs(start);
            latency.put("analyze", analyzeMs);
            agentTraceService.record(
                    requestId,
                    "QueryAnalyzerAgent",
                    "questionLength=" + question.length() + researchAreasTrace,
                    "failed",
                    AgentStepStatus.FAILED,
                    analyzeMs,
                    ex.getMessage()
            );
            return failedResponse(
                    requestId,
                    question,
                    QuestionType.INSUFFICIENT,
                    snapshot,
                    asOf,
                    latency,
                    "query_analysis_failed"
            );
        }
        latency.put("analyze", elapsedMs(start));
        agentTraceService.record(
                requestId,
                "QueryAnalyzerAgent",
                "questionLength=" + question.length(),
                "type=" + interpretation.questionType(),
                AgentStepStatus.SUCCEEDED,
                latency.get("analyze"),
                null
        );

        start = System.nanoTime();
        RetrievalResult retrievalResult;
        try {
            retrievalResult = safeResearchAreas.isEmpty()
                    ? retrievalAgent.retrieve(interpretation, asOf)
                    : retrievalAgent.retrieve(interpretation, asOf, safeResearchAreas);
        } catch (RuntimeException ex) {
            int retrieveMs = elapsedMs(start);
            latency.put("retrieve", retrieveMs);
            agentTraceService.record(
                    requestId,
                    "RetrievalAgent",
                    "queries=" + interpretation.generatedQueries().size() + researchAreasTrace,
                    "failed",
                    AgentStepStatus.FAILED,
                    retrieveMs,
                    ex.getMessage()
            );
            return failedResponse(
                    requestId,
                    question,
                    interpretation.questionType(),
                    snapshot,
                    asOf,
                    latency,
                    "retrieval_failed"
            );
        }
        List<RetrievalHit> hits = retrievalResult.hits();
        latency.put("retrieve", elapsedMs(start));
        agentTraceService.record(
                requestId,
                "RetrievalAgent",
                "queries=" + interpretation.generatedQueries().size(),
                "hits=" + hits.size(),
                AgentStepStatus.SUCCEEDED,
                latency.get("retrieve"),
                null
        );

        start = System.nanoTime();
        EvidenceDecision evidenceDecision;
        try {
            evidenceDecision = evidenceValidatorAgent.validate(interpretation.questionType(), hits);
        } catch (RuntimeException ex) {
            int validateMs = elapsedMs(start);
            latency.put("validate", validateMs);
            agentTraceService.record(
                    requestId,
                    "EvidenceValidatorAgent",
                    "hits=" + hits.size(),
                    "failed",
                    AgentStepStatus.FAILED,
                    validateMs,
                    ex.getMessage()
            );
            return failedResponse(
                    requestId,
                    question,
                    interpretation.questionType(),
                    snapshot,
                    asOf,
                    latency,
                    "evidence_validation_failed"
            );
        }
        latency.put("validate", elapsedMs(start));
        agentTraceService.record(
                requestId,
                "EvidenceValidatorAgent",
                "hits=" + hits.size() + ", type=" + interpretation.questionType(),
                "enough=" + evidenceDecision.enoughEvidence()
                        + ", weak=" + evidenceDecision.weakEvidence()
                        + ", topScore=" + String.format("%.3f", evidenceDecision.topScore()),
                AgentStepStatus.SUCCEEDED,
                latency.get("validate"),
                null
        );
        EffectiveBasisDto effectiveBasis = new EffectiveBasisDto(
                snapshot.getVersion(),
                snapshot.getIndexedAt(),
                snapshot.getSourcePath(),
                snapshot.getSourceVersion(),
                asOf
        );

        start = System.nanoTime();
        DraftAnswer reviewed;
        try {
            DraftAnswer draft = answerWriterAgent.write(
                    question,
                    interpretation,
                    hits,
                    effectiveBasis,
                    evidenceDecision.enoughEvidence(),
                    evidenceDecision.weakEvidence()
            );
            reviewed = criticAgent.review(draft);
        } catch (RuntimeException ex) {
            int synthesizeMs = elapsedMs(start);
            latency.put("synthesize", synthesizeMs);
            agentTraceService.record(
                    requestId,
                    "AnswerWriterAndCritic",
                    "hits=" + hits.size(),
                    "failed",
                    AgentStepStatus.FAILED,
                    synthesizeMs,
                    ex.getMessage()
            );
            return failedResponse(
                    requestId,
                    question,
                    interpretation.questionType(),
                    snapshot,
                    asOf,
                    latency,
                    "answer_generation_failed"
            );
        }
        latency.put("synthesize", elapsedMs(start));
        agentTraceService.record(
                requestId,
                "AnswerWriterAndCritic",
                "hits=" + hits.size(),
                "status=" + reviewed.status(),
                AgentStepStatus.SUCCEEDED,
                latency.get("synthesize"),
                null
        );

        searchLogAgent.save(
                requestId,
                question,
                interpretation.questionType(),
                reviewed.status(),
                reviewed.confidence(),
                snapshot.getVersion(),
                asOf,
                reviewed.citedArticles().size(),
                latency.get("analyze"),
                latency.get("retrieve"),
                latency.get("synthesize")
        );

        Map<String, Integer> retrievalStats = Map.of(
                "retrieved", hits.size(),
                "hydrated", hits.size(),
                "cited", reviewed.citedArticles().size(),
                "keywordHits", retrievalResult.keywordHits(),
                "vectorHits", retrievalResult.vectorHits(),
                "mergedHits", retrievalResult.mergedHits(),
                "evidenceTopScoreBp", (int) Math.round(evidenceDecision.topScore() * 1000),
                "weakEvidence", evidenceDecision.weakEvidence() ? 1 : 0
        );
        SearchDiagnosticsDto diagnostics = new SearchDiagnosticsDto(
                requestId,
                interpretation.generatedQueries(),
                retrievalStats,
                latency
        );
        askAuditLogger.logCompletion(
                requestId,
                question,
                interpretation.questionType(),
                reviewed.status(),
                snapshot.getVersion(),
                asOf,
                reviewed.citedArticles().size(),
                reviewed.candidateLaws().size(),
                reviewed.confidence(),
                latency,
                retrievalStats
        );

        return new AskResponse(
                reviewed.status(),
                interpretation,
                reviewed.candidateLaws(),
                reviewed.citedArticles(),
                reviewed.reasoning(),
                reviewed.followUpQuestions(),
                effectiveBasis,
                reviewed.confidence(),
                diagnostics,
                disclaimer,
                reviewed.status() == SearchStatus.FAILED
                        ? defaultIfBlank(reviewed.errorMessage(), "answer_generation_failed")
                        : null
        );
    }

    private AskResponse failedResponse(
            String requestId,
            String question,
            QuestionType questionType,
            SnapshotVersion snapshot,
            LocalDate asOf,
            Map<String, Integer> latency,
            String errorMessage
    ) {
        searchLogAgent.save(
                requestId,
                question,
                questionType,
                SearchStatus.FAILED,
                0.0,
                snapshot.getVersion(),
                asOf,
                0,
                latency.getOrDefault("analyze", 0),
                latency.getOrDefault("retrieve", 0),
                latency.getOrDefault("synthesize", 0)
        );
        askAuditLogger.logCompletion(
                requestId,
                question,
                questionType,
                SearchStatus.FAILED,
                snapshot.getVersion(),
                asOf,
                0,
                0,
                0.0,
                latency,
                Map.of("retrieved", 0, "cited", 0)
        );

        return new AskResponse(
                SearchStatus.FAILED,
                null,
                List.of(),
                List.of(),
                "질문 처리 중 오류가 발생했습니다. 잠시 후 다시 시도하거나 관리자 점검 화면에서 처리 이력을 확인해 주세요.",
                List.of("같은 문제가 반복되면 요청 ID를 기준으로 처리 단계를 확인해 주세요."),
                new EffectiveBasisDto(
                        snapshot.getVersion(),
                        snapshot.getIndexedAt(),
                        snapshot.getSourcePath(),
                        snapshot.getSourceVersion(),
                        asOf
                ),
                0.0,
                new SearchDiagnosticsDto(requestId, List.of(), Map.of("retrieved", 0, "cited", 0), latency),
                DEFAULT_DISCLAIMER,
                errorMessage
        );
    }

    private int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String disclaimerFor(List<ResearchArea> researchAreas) {
        boolean strategicGoods = researchAreas.contains(ResearchArea.STRATEGIC_GOODS);
        boolean defenseMaterials = researchAreas.contains(ResearchArea.DEFENSE_MATERIALS);
        if (strategicGoods && defenseMaterials) {
            return "확인 안내: 전략물자에 해당하는 경우 무역안보관리원이 안내하는 판정 및 수출허가 기준을, "
                    + "방산물자 또는 국방과학기술에 해당하는 경우 방위사업청의 수출허가 또는 예비승인 기준을 확인해야 합니다. "
                    + "물품 사양, 최종사용자, 수출국 등에 따라 적용 여부가 달라질 수 있습니다.";
        }
        if (strategicGoods) {
            return "확인 안내: 전략물자에 해당하는 경우, 수출 전에 무역안보관리원이 안내하는 전략물자 판정 및 수출허가 기준을 확인해야 합니다. "
                    + "물품 사양, 최종사용자, 수출국 등에 따라 적용 여부가 달라질 수 있습니다.";
        }
        if (defenseMaterials) {
            return "확인 안내: 방산물자 또는 국방과학기술에 해당하는 경우, 수출·중개·기술 이전 전에 "
                    + "방위사업청의 수출허가 또는 예비승인 기준을 확인해야 합니다. "
                    + "물품 사양, 최종사용자, 수출국 등에 따라 적용 여부가 달라질 수 있습니다.";
        }
        return DEFAULT_DISCLAIMER;
    }

    private String traceResearchAreas(List<ResearchArea> researchAreas) {
        return researchAreas.isEmpty() ? "" : ", researchAreas=" + researchAreas;
    }
}
