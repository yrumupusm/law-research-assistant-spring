package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.AgentTrace;
import com.example.lawassistant.domain.entity.IngestionRun;
import com.example.lawassistant.domain.entity.SearchLog;
import com.example.lawassistant.domain.entity.SyncState;
import com.example.lawassistant.dto.AgentTraceItemDto;
import com.example.lawassistant.dto.AgentTraceListResponse;
import com.example.lawassistant.domain.enums.IngestionStatus;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.dto.AdminStatusResponse;
import com.example.lawassistant.dto.IngestLocalRequest;
import com.example.lawassistant.dto.IngestLocalResponse;
import com.example.lawassistant.dto.IngestionRunItemDto;
import com.example.lawassistant.dto.IngestionRunListResponse;
import com.example.lawassistant.dto.ReindexAcceptedResponse;
import com.example.lawassistant.dto.ReindexResponse;
import com.example.lawassistant.dto.SearchLogItemDto;
import com.example.lawassistant.dto.SearchLogListResponse;
import com.example.lawassistant.dto.SyncSourceRequest;
import com.example.lawassistant.dto.SyncSourceResponse;
import com.example.lawassistant.dto.SyncStateDto;
import com.example.lawassistant.repository.AgentTraceRepository;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.repository.IngestionRunRepository;
import com.example.lawassistant.repository.LawRepository;
import com.example.lawassistant.repository.SearchLogRepository;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import com.example.lawassistant.repository.SyncStateRepository;
import com.example.lawassistant.service.model.VectorIndexResult;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final SnapshotVersionRepository snapshotVersionRepository;
    private final LawRepository lawRepository;
    private final ArticleRepository articleRepository;
    private final SearchLogRepository searchLogRepository;
    private final AgentTraceRepository agentTraceRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final SyncStateRepository syncStateRepository;
    private final VectorIndexService vectorIndexService;
    private final LocalLawIngestionService localLawIngestionService;
    private final SourceSyncService sourceSyncService;
    private final TaskExecutor taskExecutor;
    private final boolean reindexEnabled;

    public AdminService(
            SnapshotVersionRepository snapshotVersionRepository,
            LawRepository lawRepository,
            ArticleRepository articleRepository,
            SearchLogRepository searchLogRepository,
            AgentTraceRepository agentTraceRepository,
            IngestionRunRepository ingestionRunRepository,
            SyncStateRepository syncStateRepository,
            VectorIndexService vectorIndexService,
            LocalLawIngestionService localLawIngestionService,
            SourceSyncService sourceSyncService,
            TaskExecutor taskExecutor,
            @Value("${app.admin.reindex-enabled:false}") boolean reindexEnabled
    ) {
        this.snapshotVersionRepository = snapshotVersionRepository;
        this.lawRepository = lawRepository;
        this.articleRepository = articleRepository;
        this.searchLogRepository = searchLogRepository;
        this.agentTraceRepository = agentTraceRepository;
        this.ingestionRunRepository = ingestionRunRepository;
        this.syncStateRepository = syncStateRepository;
        this.vectorIndexService = vectorIndexService;
        this.localLawIngestionService = localLawIngestionService;
        this.sourceSyncService = sourceSyncService;
        this.taskExecutor = taskExecutor;
        this.reindexEnabled = reindexEnabled;
    }

    @Transactional(readOnly = true)
    public AdminStatusResponse status() {
        var snapshot = snapshotVersionRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED);
        long articlesCount = articleRepository.count();
        long indexedArticlesCount = vectorIndexService.indexedCount();
        String indexStatus = indexStatus(snapshot.isPresent(), articlesCount, indexedArticlesCount);
        return new AdminStatusResponse(
                snapshot.map(value -> value.getVersion()).orElse(null),
                snapshot.map(value -> value.getIndexedAt()).orElse(null),
                indexStatus,
                lawRepository.count(),
                articlesCount,
                indexedArticlesCount,
                Math.max(0, articlesCount - indexedArticlesCount),
                searchLogRepository.count(),
                recentFailures(),
                reindexEnabled,
                syncStateRepository.findById(SyncState.SINGLETON_ID)
                        .map(this::toSyncStateDto)
                        .orElse(null)
        );
    }

    private java.util.List<IngestionRunItemDto> recentFailures() {
        var failures = ingestionRunRepository.findTop5ByStatusOrderByStartedAtDesc(IngestionStatus.FAILED);
        return failures == null
                ? java.util.List.of()
                : failures.stream().map(this::toIngestionRunItem).toList();
    }

    @Transactional(readOnly = true)
    public SearchLogListResponse searchLogs() {
        var logs = searchLogRepository.findTop50ByOrderByCreatedAtDesc();
        return new SearchLogListResponse(
                searchLogRepository.count(),
                logs.stream().map(this::toSearchLogItem).toList()
        );
    }

    @Transactional(readOnly = true)
    public AgentTraceListResponse agentTraces(String requestId) {
        boolean filtered = requestId != null && !requestId.isBlank();
        var traces = filtered
                ? agentTraceRepository.findTop100ByRequestIdOrderByCreatedAtAsc(requestId.trim())
                : agentTraceRepository.findTop100ByOrderByCreatedAtDesc();
        return new AgentTraceListResponse(
                filtered ? agentTraceRepository.countByRequestId(requestId.trim()) : agentTraceRepository.count(),
                traces.stream().map(this::toAgentTraceItem).toList()
        );
    }

    public ReindexResponse reindex() {
        assertReindexEnabled();
        var snapshot = snapshotVersionRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED);
        IngestionRun run = ingestionRunRepository.save(IngestionRun.start(snapshot.orElse(null)));
        try {
            VectorIndexResult result = executeReindex(run);
            return new ReindexResponse(
                    run.getId(),
                    run.getStatus(),
                    result.indexedArticles(),
                    result.failedArticles(),
                    run.getErrorMessage(),
                    run.getSnapshotVersion() == null ? null : run.getSnapshotVersion().getVersion(),
                    run.getStartedAt(),
                    run.getFinishedAt()
            );
        } catch (RuntimeException ex) {
            run.fail(ex.getMessage());
            ingestionRunRepository.save(run);
            throw ex;
        }
    }

    public ReindexAcceptedResponse scheduleReindex() {
        assertReindexEnabled();
        var snapshot = snapshotVersionRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED);
        IngestionRun run = ingestionRunRepository.save(IngestionRun.start(snapshot.orElse(null)));
        taskExecutor.execute(() -> completeScheduledReindex(run.getId()));
        return new ReindexAcceptedResponse(
                "accepted",
                "reindex scheduled",
                run.getId(),
                run.getSnapshotVersion() == null ? null : run.getSnapshotVersion().getVersion(),
                run.getStartedAt()
        );
    }

    private void completeScheduledReindex(Long runId) {
        ingestionRunRepository.findById(runId).ifPresent(run -> {
            try {
                executeReindex(run);
            } catch (RuntimeException ex) {
                run.fail(ex.getMessage());
                ingestionRunRepository.save(run);
            }
        });
    }

    private VectorIndexResult executeReindex(IngestionRun run) {
        VectorIndexResult result = vectorIndexService.reindexAllDetailed();
        if (result.hasFailures()) {
            run.complete(result.indexedArticles(), result.failedArticles(), result.failureSummary());
        } else {
            run.succeed(result.indexedArticles());
        }
        ingestionRunRepository.save(run);
        return result;
    }

    private void assertReindexEnabled() {
        if (!reindexEnabled) {
            throw new AdminOperationDisabledException(
                    "admin reindex is disabled. Set ADMIN_REINDEX_ENABLED=true and restart the application to enable."
            );
        }
    }

    public IngestLocalResponse ingestLocal(IngestLocalRequest request) {
        Path sourceDir = request == null || request.sourceDir() == null || request.sourceDir().isBlank()
                ? null
                : Path.of(request.sourceDir());
        var result = localLawIngestionService.ingest(
                sourceDir,
                request == null ? null : request.snapshotPrefix()
        );
        return new IngestLocalResponse(
                result.run().getId(),
                result.run().getStatus(),
                result.snapshotVersion(),
                result.filesProcessed(),
                result.filesFailed(),
                result.lawsImported(),
                result.articlesImported(),
                result.indexedArticles(),
                result.run().getErrorMessage(),
                result.run().getStartedAt(),
                result.run().getFinishedAt()
        );
    }

    public SyncSourceResponse syncSource(SyncSourceRequest request) {
        return sourceSyncService.sync(request);
    }

    @Transactional(readOnly = true)
    public IngestionRunListResponse ingestionRuns() {
        var runs = ingestionRunRepository.findTop20ByOrderByStartedAtDesc();
        return new IngestionRunListResponse(
                ingestionRunRepository.count(),
                runs.stream().map(this::toIngestionRunItem).toList()
        );
    }

    private SearchLogItemDto toSearchLogItem(SearchLog log) {
        return new SearchLogItemDto(
                log.getId(),
                log.getRequestId(),
                log.getQuestionHash(),
                log.getQuestionPreview(),
                log.getQuestionLength(),
                log.getQuestionType(),
                log.getStatus(),
                log.getConfidence(),
                log.getSnapshotVersion(),
                log.getAsOf(),
                log.getCitedArticleCount(),
                log.getLatencyAnalyzeMs(),
                log.getLatencyRetrieveMs(),
                log.getLatencySynthesizeMs(),
                log.getCreatedAt()
        );
    }

    private AgentTraceItemDto toAgentTraceItem(AgentTrace trace) {
        return new AgentTraceItemDto(
                trace.getId(),
                trace.getRequestId(),
                trace.getStepName(),
                trace.getInputSummary(),
                trace.getOutputSummary(),
                trace.getStatus(),
                trace.getLatencyMs(),
                trace.getErrorMessage(),
                trace.getCreatedAt()
        );
    }

    private IngestionRunItemDto toIngestionRunItem(IngestionRun run) {
        return new IngestionRunItemDto(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus(),
                run.getFilesProcessed(),
                run.getFilesFailed(),
                run.getSnapshotVersion() == null ? null : run.getSnapshotVersion().getVersion(),
                run.getErrorMessage()
        );
    }

    private SyncStateDto toSyncStateDto(SyncState state) {
        return new SyncStateDto(
                state.getLastSyncedCommitSha(),
                state.getLastSyncAt(),
                state.getLastForcePushDetectedAt()
        );
    }

    private String indexStatus(boolean hasIndexedSnapshot, long articlesCount, long indexedArticlesCount) {
        if (!hasIndexedSnapshot || articlesCount == 0) {
            return "missing";
        }
        if (articlesCount != indexedArticlesCount) {
            return "stale";
        }
        return "healthy";
    }
}
