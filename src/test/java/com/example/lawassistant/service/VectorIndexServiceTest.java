package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.example.lawassistant.infrastructure.vector.VectorDocument;
import com.example.lawassistant.infrastructure.vector.VectorSearchClient;
import com.example.lawassistant.repository.ArticleRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class VectorIndexServiceTest {

    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final VectorSearchClient vectorSearchClient = mock(VectorSearchClient.class);

    @Test
    void reindexAllDetailedIndexesArticlesInConfiguredBatches() {
        when(articleRepository.findAll()).thenReturn(List.of(
                article(1L, "제1조", "목적"),
                article(2L, "제2조", "허가"),
                article(3L, "제3조", "신고")
        ));
        when(embeddingClient.embedAll(anyList())).thenAnswer(invocation -> vectorsFor(invocation.getArgument(0)));

        VectorIndexService service = new VectorIndexService(
                articleRepository,
                embeddingClient,
                vectorSearchClient,
                "law_articles",
                2
        );

        var result = service.reindexAllDetailed();

        assertThat(result.totalArticles()).isEqualTo(3);
        assertThat(result.indexedArticles()).isEqualTo(3);
        assertThat(result.failedArticleIds()).isEmpty();
        assertThat(service.indexedCount()).isEqualTo(3);
        verify(embeddingClient, times(2)).embedAll(anyList());
        verify(vectorSearchClient, times(2)).upsert(org.mockito.ArgumentMatchers.eq("law_articles"), anyList());
    }

    @Test
    void reindexAllDetailedRetriesSingleArticlesWhenBatchFails() {
        when(articleRepository.findAll()).thenReturn(List.of(
                article(1L, "제1조", "목적"),
                article(2L, "제2조", "허가"),
                article(3L, "제3조", "신고")
        ));
        when(embeddingClient.embedAll(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            if (texts.size() > 1) {
                throw new IllegalStateException("batch failed");
            }
            if (texts.get(0).contains("제2조")) {
                throw new IllegalStateException("single failed");
            }
            return vectorsFor(texts);
        });

        VectorIndexService service = new VectorIndexService(
                articleRepository,
                embeddingClient,
                vectorSearchClient,
                "law_articles",
                3
        );

        var result = service.reindexAllDetailed();

        assertThat(result.totalArticles()).isEqualTo(3);
        assertThat(result.indexedArticles()).isEqualTo(2);
        assertThat(result.failedArticleIds()).containsExactly(2L);
        assertThat(result.failureSummary()).contains("indexed 2/3").contains("2");
        assertThat(service.indexedCount()).isEqualTo(2);

        ArgumentCaptor<List<VectorDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorSearchClient, times(2)).upsert(org.mockito.ArgumentMatchers.eq("law_articles"), captor.capture());
        assertThat(captor.getAllValues())
                .flatExtracting(documents -> documents.stream().map(VectorDocument::id).toList())
                .containsExactly("1", "3");
    }

    private List<List<Double>> vectorsFor(List<String> texts) {
        return texts.stream()
                .map(ignored -> List.of(0.1, 0.2, 0.3))
                .toList();
    }

    private Article article(Long id, String number, String title) {
        SnapshotVersion snapshot = new SnapshotVersion(
                "law-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.parse("2026-06-16T01:00:00"),
                null
        );
        Law law = new Law("law:대외무역법", "대외무역법", LawType.LAW, "법률 제1호", snapshot);
        ReflectionTestUtils.setField(law, "id", 10L);
        Article article = new Article(law, number, title, title + " 내용", id.intValue());
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
