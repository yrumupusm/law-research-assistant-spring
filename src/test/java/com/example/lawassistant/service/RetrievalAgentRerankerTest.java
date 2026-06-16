package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.dto.QuestionInterpretationDto;
import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.example.lawassistant.infrastructure.rerank.RerankCandidate;
import com.example.lawassistant.infrastructure.rerank.RerankerClient;
import com.example.lawassistant.infrastructure.vector.VectorSearchClient;
import com.example.lawassistant.repository.ArticleRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RetrievalAgentRerankerTest {

    @Test
    void retrieveUsesRerankerOrderForFinalHits() {
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorSearchClient vectorSearchClient = mock(VectorSearchClient.class);
        VectorIndexService vectorIndexService = mock(VectorIndexService.class);
        RerankerClient rerankerClient = (query, candidates, topK) -> candidates.stream()
                .sorted(Comparator.comparing(candidate -> candidate.metadata().get("articleNumber").toString()))
                .limit(topK)
                .map(candidate -> new RerankCandidate(candidate.id(), candidate.text(), 9.0, candidate.metadata()))
                .toList();

        Article highBaseScore = article(1L, "제99조", "전략물자 수출 허가 절차를 일반적으로 설명합니다.");
        Article rerankedFirst = article(2L, "제10조", "무역안보관리원 법적 근거와 역할을 설명합니다.");
        when(articleRepository.searchByKeyword(eq("무역안보관리원"))).thenReturn(List.of(highBaseScore, rerankedFirst));
        when(embeddingClient.embed(any())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(vectorSearchClient.search(eq("law_articles"), any(), eq(5))).thenReturn(List.of());

        RetrievalAgent agent = new RetrievalAgent(
                articleRepository,
                embeddingClient,
                vectorSearchClient,
                vectorIndexService,
                rerankerClient,
                2,
                5,
                "law_articles"
        );

        var result = agent.retrieve(new QuestionInterpretationDto(
                "근거확인",
                "무역안보관리원",
                List.of("무역안보"),
                List.of(),
                List.of("무역안보관리원"),
                QuestionType.CONFIRMATORY
        ));

        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).article().getId()).isEqualTo(2L);
        assertThat(result.hits().get(0).reason()).contains("재정렬 점수");
    }

    @Test
    void keywordRankingBoostsExactLawAndArticleNumberMatches() {
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorSearchClient vectorSearchClient = mock(VectorSearchClient.class);
        VectorIndexService vectorIndexService = mock(VectorIndexService.class);
        RerankerClient rerankerClient = (query, candidates, topK) -> candidates.stream()
                .sorted(Comparator.comparingDouble(RerankCandidate::score).reversed())
                .limit(topK)
                .toList();

        Article customsDeclaration = article(10L, "관세법", "제241조", "수출 신고에서는 품명, 규격, 수량, 가격을 확인합니다.");
        Article customsPrice = article(11L, "관세법", "제270조의2", "가격 조작과 관세 포탈 관련 조문입니다.");
        when(articleRepository.searchByKeyword(eq("관세법"))).thenReturn(List.of(customsDeclaration, customsPrice));
        when(articleRepository.searchByKeyword(eq("제241조"))).thenReturn(List.of(customsDeclaration));
        when(articleRepository.searchByKeyword(eq("수출 신고"))).thenReturn(List.of(customsDeclaration));
        when(embeddingClient.embed(any())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(vectorSearchClient.search(eq("law_articles"), any(), eq(5))).thenReturn(List.of());

        RetrievalAgent agent = new RetrievalAgent(
                articleRepository,
                embeddingClient,
                vectorSearchClient,
                vectorIndexService,
                rerankerClient,
                2,
                5,
                "law_articles"
        );

        var result = agent.retrieve(new QuestionInterpretationDto(
                "수출",
                "관세법",
                List.of("통관"),
                List.of(),
                List.of("관세법", "제241조", "수출 신고"),
                QuestionType.CONFIRMATORY
        ));

        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).article().getId()).isEqualTo(10L);
        assertThat(result.hits().get(0).score()).isGreaterThan(result.hits().get(1).score());
    }

    @Test
    void keywordRankingUsesTokenFrequencyAndReciprocalRankSignal() {
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorSearchClient vectorSearchClient = mock(VectorSearchClient.class);
        VectorIndexService vectorIndexService = mock(VectorIndexService.class);
        RerankerClient rerankerClient = (query, candidates, topK) -> candidates.stream()
                .sorted(Comparator.comparingDouble(RerankCandidate::score).reversed())
                .limit(topK)
                .toList();

        Article repeatedPermit = article(30L, "대외무역법", "제19조", "허가 허가 허가 절차와 최종 사용자 확인을 설명합니다.");
        Article singlePermit = article(31L, "대외무역법", "제20조", "허가 절차를 설명합니다.");
        when(articleRepository.searchByKeyword(eq("허가"))).thenReturn(List.of(singlePermit, repeatedPermit));
        when(embeddingClient.embed(any())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(vectorSearchClient.search(eq("law_articles"), any(), eq(5))).thenReturn(List.of());

        RetrievalAgent agent = new RetrievalAgent(
                articleRepository,
                embeddingClient,
                vectorSearchClient,
                vectorIndexService,
                rerankerClient,
                2,
                5,
                "law_articles"
        );

        var result = agent.retrieve(new QuestionInterpretationDto(
                "허가",
                "수출 허가",
                List.of("허가"),
                List.of(),
                List.of("허가"),
                QuestionType.CONFIRMATORY
        ));

        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0).article().getId()).isEqualTo(30L);
        assertThat(result.hits().get(0).reason())
                .contains("lexical=")
                .contains("rank=1")
                .contains("rrf=");
    }

    @Test
    void vectorRetrievalExcludesLegacyArticleWhenAsOfIsSpecified() {
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        VectorSearchClient vectorSearchClient = mock(VectorSearchClient.class);
        VectorIndexService vectorIndexService = mock(VectorIndexService.class);
        RerankerClient rerankerClient = (query, candidates, topK) -> candidates.stream().limit(topK).toList();

        Article legacy = article(20L, "대외무역법", "제1조", "legacy 조문");
        when(articleRepository.searchByKeywordAsOf(eq("legacy"), eq(LocalDate.of(2026, 1, 1)))).thenReturn(List.of());
        when(embeddingClient.embed(any())).thenReturn(List.of(0.1, 0.2, 0.3));
        when(vectorSearchClient.search(eq("law_articles"), any(), eq(5)))
                .thenReturn(List.of(new com.example.lawassistant.infrastructure.vector.VectorSearchResult("20", 0.99, java.util.Map.of())));
        when(articleRepository.findById(20L)).thenReturn(Optional.of(legacy));

        RetrievalAgent agent = new RetrievalAgent(
                articleRepository,
                embeddingClient,
                vectorSearchClient,
                vectorIndexService,
                rerankerClient,
                5,
                5,
                "law_articles"
        );

        var result = agent.retrieve(new QuestionInterpretationDto(
                "조회",
                "legacy",
                List.of(),
                List.of(),
                List.of("legacy"),
                QuestionType.EXPLORATORY
        ), LocalDate.of(2026, 1, 1));

        assertThat(result.hits()).isEmpty();
        assertThat(result.vectorHits()).isZero();
    }

    private Article article(Long id, String articleNumber, String content) {
        return article(id, "대외무역법", articleNumber, content);
    }

    private Article article(Long id, String lawTitle, String articleNumber, String content) {
        SnapshotVersion snapshot = new SnapshotVersion(
                "test-snapshot",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        Law law = new Law("law:test:" + lawTitle, lawTitle, LawType.LAW, "LAW-TEST", snapshot);
        Article article = new Article(law, articleNumber, "테스트 조문", content, 0);
        ReflectionTestUtils.setField(law, "id", 1L);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
