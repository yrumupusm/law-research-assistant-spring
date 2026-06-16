package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.entity.SyncState;
import com.example.lawassistant.domain.enums.IngestionStatus;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.repository.AgentTraceRepository;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.repository.IngestionRunRepository;
import com.example.lawassistant.repository.LawRepository;
import com.example.lawassistant.repository.SearchLogRepository;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import com.example.lawassistant.repository.SyncStateRepository;
import com.example.lawassistant.service.model.VectorIndexResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.core.task.SyncTaskExecutor;
import org.junit.jupiter.api.Test;

class AdminServiceTest {

    private final SnapshotVersionRepository snapshotVersionRepository = mock(SnapshotVersionRepository.class);
    private final LawRepository lawRepository = mock(LawRepository.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final SearchLogRepository searchLogRepository = mock(SearchLogRepository.class);
    private final AgentTraceRepository agentTraceRepository = mock(AgentTraceRepository.class);
    private final IngestionRunRepository ingestionRunRepository = mock(IngestionRunRepository.class);
    private final SyncStateRepository syncStateRepository = mock(SyncStateRepository.class);
    private final VectorIndexService vectorIndexService = mock(VectorIndexService.class);
    private final LocalLawIngestionService localLawIngestionService = mock(LocalLawIngestionService.class);
    private final SourceSyncService sourceSyncService = mock(SourceSyncService.class);

    private final AdminService service = new AdminService(
            snapshotVersionRepository,
            lawRepository,
            articleRepository,
            searchLogRepository,
            agentTraceRepository,
            ingestionRunRepository,
            syncStateRepository,
            vectorIndexService,
            localLawIngestionService,
            sourceSyncService,
            new SyncTaskExecutor(),
            true
    );

    @Test
    void statusMarksIndexStaleWhenIndexedArticleCountDiffersFromStoredArticles() {
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.parse("2026-06-16T01:00:00"),
                null
        );
        when(snapshotVersionRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.of(snapshot));
        when(lawRepository.count()).thenReturn(2L);
        when(articleRepository.count()).thenReturn(10L);
        when(vectorIndexService.indexedCount()).thenReturn(7);
        when(searchLogRepository.count()).thenReturn(3L);
        when(syncStateRepository.findById(SyncState.SINGLETON_ID)).thenReturn(Optional.empty());

        var response = service.status();

        assertThat(response.indexStatus()).isEqualTo("stale");
        assertThat(response.articlesCount()).isEqualTo(10);
        assertThat(response.indexedArticlesCount()).isEqualTo(7);
        assertThat(response.unindexedArticlesCount()).isEqualTo(3);
    }

    @Test
    void statusMarksIndexMissingWhenNoIndexedSnapshotExists() {
        when(snapshotVersionRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.empty());
        when(articleRepository.count()).thenReturn(10L);
        when(vectorIndexService.indexedCount()).thenReturn(10);
        when(syncStateRepository.findById(SyncState.SINGLETON_ID)).thenReturn(Optional.empty());

        var response = service.status();

        assertThat(response.indexStatus()).isEqualTo("missing");
        assertThat(response.indexedArticlesCount()).isEqualTo(10);
        assertThat(response.unindexedArticlesCount()).isZero();
    }

    @Test
    void reindexReturnsPartialFailureDetailsWhenSomeArticlesFail() {
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-domain-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.parse("2026-06-16T01:00:00"),
                null
        );
        when(snapshotVersionRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED))
                .thenReturn(Optional.of(snapshot));
        when(ingestionRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vectorIndexService.reindexAllDetailed()).thenReturn(new VectorIndexResult(3, 2, List.of(2L)));

        var response = service.reindex();

        assertThat(response.status()).isEqualTo(IngestionStatus.PARTIAL_FAILED);
        assertThat(response.indexedArticles()).isEqualTo(2);
        assertThat(response.failedArticles()).isEqualTo(1);
        assertThat(response.errorMessage()).contains("indexed 2/3").contains("2");
    }
}
