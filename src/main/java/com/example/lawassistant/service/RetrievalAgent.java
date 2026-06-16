package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.dto.QuestionInterpretationDto;
import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.example.lawassistant.infrastructure.rerank.RerankCandidate;
import com.example.lawassistant.infrastructure.rerank.RerankerClient;
import com.example.lawassistant.infrastructure.vector.VectorSearchClient;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.service.model.RetrievalHit;
import com.example.lawassistant.service.model.RetrievalResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalAgent {

    private static final int RRF_K = 60;
    private static final double RRF_WEIGHT = 6.0;

    private final ArticleRepository articleRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorSearchClient vectorSearchClient;
    private final VectorIndexService vectorIndexService;
    private final RerankerClient rerankerClient;
    private final int topK;
    private final int vectorTopK;
    private final String collectionName;

    public RetrievalAgent(
            ArticleRepository articleRepository,
            EmbeddingClient embeddingClient,
            VectorSearchClient vectorSearchClient,
            VectorIndexService vectorIndexService,
            RerankerClient rerankerClient,
            @Value("${app.rag.top-k:5}") int topK,
            @Value("${app.vector.top-k:5}") int vectorTopK,
            @Value("${app.vector.collection:law_articles}") String collectionName
    ) {
        this.articleRepository = articleRepository;
        this.embeddingClient = embeddingClient;
        this.vectorSearchClient = vectorSearchClient;
        this.vectorIndexService = vectorIndexService;
        this.rerankerClient = rerankerClient;
        this.topK = topK;
        this.vectorTopK = vectorTopK;
        this.collectionName = collectionName;
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieve(QuestionInterpretationDto interpretation) {
        return retrieve(interpretation, null);
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieve(QuestionInterpretationDto interpretation, LocalDate asOf) {
        if (interpretation.questionType() == QuestionType.INSUFFICIENT) {
            return new RetrievalResult(List.of(), 0, 0, 0);
        }

        Map<Long, MutableHit> merged = new LinkedHashMap<>();
        int keywordHits = collectKeywordHits(interpretation, asOf, merged);
        int vectorHits = collectVectorHits(interpretation, asOf, merged);

        List<RetrievalHit> hits = rerank(
                interpretation,
                merged.values().stream()
                .map(MutableHit::toHit)
                .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
                .toList()
        );

        return new RetrievalResult(hits, keywordHits, vectorHits, merged.size());
    }

    private List<RetrievalHit> rerank(QuestionInterpretationDto interpretation, List<RetrievalHit> hits) {
        if (hits.isEmpty()) {
            return hits;
        }
        Map<String, RetrievalHit> byId = new LinkedHashMap<>();
        List<RerankCandidate> candidates = new ArrayList<>();
        for (RetrievalHit hit : hits) {
            String id = String.valueOf(hit.article().getId());
            byId.put(id, hit);
            candidates.add(new RerankCandidate(
                    id,
                    rerankText(hit.article()),
                    hit.score(),
                    Map.of(
                            "lawTitle", hit.article().getLaw().getTitle(),
                            "articleNumber", hit.article().getArticleNumber(),
                            "baseScore", hit.score()
                    )
            ));
        }
        String query = String.join(" ", interpretation.generatedQueries());
        return rerankerClient.rerank(query, candidates, topK).stream()
                .map(candidate -> withRerankScore(byId.get(candidate.id()), candidate.score()))
                .filter(hit -> hit != null)
                .toList();
    }

    private String rerankText(Article article) {
        return article.getLaw().getTitle()
                + " "
                + article.getArticleNumber()
                + " "
                + (article.getArticleTitle() == null ? "" : article.getArticleTitle())
                + "\n"
                + article.getContent();
    }

    private RetrievalHit withRerankScore(RetrievalHit hit, double rerankScore) {
        if (hit == null) {
            return null;
        }
        return new RetrievalHit(
                hit.article(),
                rerankScore,
                hit.reason() + "; 재정렬 점수: " + formatScore(rerankScore)
        );
    }

    private int collectKeywordHits(QuestionInterpretationDto interpretation, LocalDate asOf, Map<Long, MutableHit> merged) {
        int count = 0;
        for (String query : interpretation.generatedQueries()) {
            List<Article> articles = asOf == null
                    ? articleRepository.searchByKeyword(query)
                    : articleRepository.searchByKeywordAsOf(query, asOf);
            List<ScoredArticle> scoredArticles = articles.stream()
                    .map(article -> new ScoredArticle(article, keywordScore(article, query)))
                    .sorted(Comparator.comparingDouble(ScoredArticle::score).reversed())
                    .toList();
            int rank = 1;
            for (ScoredArticle scored : scoredArticles) {
                count++;
                double rankScore = reciprocalRankScore(rank);
                merge(
                        merged,
                        scored.article(),
                        scored.score() + rankScore,
                        "키워드 일치: " + query
                                + " (lexical=" + formatScore(scored.score())
                                + ", rank=" + rank
                                + ", rrf=" + formatScore(rankScore) + ")"
                );
                rank++;
            }
        }
        return count;
    }

    private int collectVectorHits(QuestionInterpretationDto interpretation, LocalDate asOf, Map<Long, MutableHit> merged) {
        String queryText = String.join(" ", interpretation.generatedQueries());
        if (queryText.isBlank()) {
            return 0;
        }
        vectorIndexService.ensureIndexed();
        List<Double> queryVector = embeddingClient.embed(queryText);
        var vectorResults = vectorSearchClient.search(collectionName, queryVector, vectorTopK);
        int count = 0;
        int rank = 1;
        for (var result : vectorResults) {
            long articleId = Long.parseLong(result.id());
            var article = articleRepository.findById(articleId)
                    .filter(value -> isEffectiveOn(value, asOf));
            if (article.isPresent()) {
                double rankScore = reciprocalRankScore(rank);
                merge(
                        merged,
                        article.get(),
                        0.7 + Math.max(0.0, result.score()) + rankScore,
                        "벡터 유사도: " + formatScore(result.score())
                                + " (rank=" + rank
                                + ", rrf=" + formatScore(rankScore) + ")"
                );
                count++;
            }
            rank++;
        }
        return count;
    }

    private void merge(Map<Long, MutableHit> merged, Article article, double score, String reason) {
        MutableHit hit = merged.computeIfAbsent(article.getId(), ignored -> new MutableHit(article));
        hit.score += score;
        hit.reasons.add(reason);
    }

    private double keywordScore(Article article, String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return 0.0;
        }
        String lawTitle = normalize(article.getLaw().getTitle());
        String articleNumber = normalize(article.getArticleNumber());
        String articleTitle = normalize(article.getArticleTitle());
        String content = normalize(article.getContent());
        String compactQuery = compact(normalized);
        String compactLawTitle = compact(lawTitle);
        String compactArticleNumber = compact(articleNumber);

        double score = 0.05;
        if (!articleNumber.isBlank() && normalized.contains(articleNumber)) {
            score += 2.2;
        }
        if (!compactArticleNumber.isBlank() && compactQuery.contains(compactArticleNumber)) {
            score += 1.2;
        }
        if (!lawTitle.isBlank() && normalized.contains(lawTitle)) {
            score += 1.4;
        }
        if (!compactLawTitle.isBlank() && compactQuery.contains(compactLawTitle)) {
            score += 0.8;
        }
        if (!articleTitle.isBlank() && articleTitle.contains(normalized)) {
            score += 1.1;
        }
        if (content.contains(normalized)) {
            score += 0.9 + Math.min(0.4, normalized.length() / 120.0);
        }

        for (String token : meaningfulTokens(normalized)) {
            if (articleNumber.contains(token)) {
                score += 0.7;
            }
            if (lawTitle.contains(token)) {
                score += 0.45;
            }
            if (articleTitle.contains(token)) {
                score += 0.35;
            }
            if (content.contains(token)) {
                score += 0.18;
            }
            score += termFrequencyScore(articleNumber, token, 0.45);
            score += termFrequencyScore(lawTitle, token, 0.30);
            score += termFrequencyScore(articleTitle, token, 0.24);
            score += termFrequencyScore(content, token, 0.12);
        }
        return score;
    }

    private double termFrequencyScore(String field, String token, double weight) {
        if (field == null || field.isBlank() || token == null || token.isBlank()) {
            return 0.0;
        }
        int occurrences = countOccurrences(field, token);
        if (occurrences == 0) {
            return 0.0;
        }
        double lengthPenalty = 1.0 + Math.log10(Math.max(10, field.length()));
        return Math.min(0.8, (occurrences * weight) / lengthPenalty);
    }

    private int countOccurrences(String value, String token) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private double reciprocalRankScore(int rank) {
        return RRF_WEIGHT / (RRF_K + Math.max(1, rank));
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase()
                        .replaceAll("\\s+", " ")
                        .strip();
    }

    private String compact(String value) {
        return value == null
                ? ""
                : value.toLowerCase()
                        .replaceAll("[\\s·ㆍ\\-_/()]+", "")
                        .strip();
    }

    private Set<String> meaningfulTokens(String value) {
        Set<String> tokens = new HashSet<>();
        for (String token : value.split("[^0-9a-z가-힣]+")) {
            String normalized = token.strip();
            if (normalized.length() >= 2) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }

    private String formatScore(double score) {
        return String.format("%.3f", score);
    }

    private boolean isEffectiveOn(Article article, LocalDate asOf) {
        if (asOf == null) {
            return article.getEffectiveTo() == null;
        }
        if (article.getEffectiveFrom() == null && article.getEffectiveTo() == null) {
            return false;
        }
        boolean startsBefore = article.getEffectiveFrom() == null || !article.getEffectiveFrom().isAfter(asOf);
        boolean endsAfter = article.getEffectiveTo() == null || !article.getEffectiveTo().isBefore(asOf);
        return startsBefore && endsAfter;
    }

    private static class MutableHit {
        private final Article article;
        private double score;
        private final List<String> reasons = new ArrayList<>();

        private MutableHit(Article article) {
            this.article = article;
        }

        private RetrievalHit toHit() {
            return new RetrievalHit(article, score, String.join("; ", reasons));
        }
    }

    private record ScoredArticle(Article article, double score) {
    }
}
