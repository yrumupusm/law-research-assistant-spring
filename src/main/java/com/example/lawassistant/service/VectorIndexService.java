package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.infrastructure.embedding.EmbeddingClient;
import com.example.lawassistant.infrastructure.openrouter.OpenRouterClientException;
import com.example.lawassistant.infrastructure.vector.VectorDocument;
import com.example.lawassistant.infrastructure.vector.VectorSearchClient;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.service.model.VectorIndexResult;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexService.class);

    private final ArticleRepository articleRepository;
    private final EmbeddingClient embeddingClient;
    private final VectorSearchClient vectorSearchClient;
    private final String collectionName;
    private final int batchSize;
    private final long batchDelayMillis;
    private final long rateLimitRetryDelayMillis;
    private final int rateLimitMaxRetries;
    private final AtomicInteger indexedCount = new AtomicInteger();

    public VectorIndexService(
            ArticleRepository articleRepository,
            EmbeddingClient embeddingClient,
            VectorSearchClient vectorSearchClient,
            @Value("${app.vector.collection:law_articles}") String collectionName,
            @Value("${app.embedding.batch-size:32}") int batchSize,
            @Value("${app.embedding.batch-delay-millis:0}") long batchDelayMillis,
            @Value("${app.embedding.rate-limit-retry-delay-millis:30000}") long rateLimitRetryDelayMillis,
            @Value("${app.embedding.rate-limit-max-retries:2}") int rateLimitMaxRetries
    ) {
        this.articleRepository = articleRepository;
        this.embeddingClient = embeddingClient;
        this.vectorSearchClient = vectorSearchClient;
        this.collectionName = collectionName;
        this.batchSize = Math.max(1, batchSize);
        this.batchDelayMillis = Math.max(0, batchDelayMillis);
        this.rateLimitRetryDelayMillis = Math.max(0, rateLimitRetryDelayMillis);
        this.rateLimitMaxRetries = Math.max(0, rateLimitMaxRetries);
    }

    @PostConstruct
    void restorePersistedIndexCount() {
        long persistedCount = vectorSearchClient.count(collectionName);
        indexedCount.set(Math.toIntExact(persistedCount));
        log.info("Restored vector index state. collection={} points={}", collectionName, persistedCount);
    }

    @Transactional(readOnly = true)
    public int reindexAll() {
        VectorIndexResult result = reindexAllDetailed();
        if (result.hasFailures()) {
            throw new IllegalStateException(result.failureSummary());
        }
        return result.indexedArticles();
    }

    @Transactional(readOnly = true)
    public VectorIndexResult reindexAllDetailed() {
        List<Article> articles = articleRepository.findAll();
        if (articles.isEmpty()) {
            indexedCount.set(0);
            return new VectorIndexResult(0, 0, List.of());
        }

        int indexed = 0;
        List<Long> failedArticleIds = new ArrayList<>();
        for (int start = 0; start < articles.size(); start += batchSize) {
            int end = Math.min(start + batchSize, articles.size());
            List<Article> batch = articles.subList(start, end);
            try {
                indexed += embedAndUpsertWithRateLimitRetry(batch, start, end);
            } catch (RuntimeException batchFailure) {
                log.warn(
                        "Vector batch indexing failed. start={} end={} size={} reason={}",
                        start,
                        end,
                        batch.size(),
                        failureCode(batchFailure)
                );
                if (isRateLimited(batchFailure)) {
                    failedArticleIds.addAll(batch.stream().map(Article::getId).toList());
                } else {
                    indexed += retryIndividually(batch, failedArticleIds);
                }
            }
            pause(batchDelayMillis);
        }

        indexedCount.set(indexed);
        return new VectorIndexResult(articles.size(), indexed, failedArticleIds);
    }

    private int retryIndividually(List<Article> articles, List<Long> failedArticleIds) {
        int indexed = 0;
        for (Article article : articles) {
            try {
                indexed += embedAndUpsert(List.of(article));
            } catch (RuntimeException singleFailure) {
                log.warn(
                        "Vector single-article indexing failed. articleId={} reason={}",
                        article.getId(),
                        failureCode(singleFailure)
                );
                failedArticleIds.add(article.getId());
            }
        }
        return indexed;
    }

    private int embedAndUpsertWithRateLimitRetry(List<Article> batch, int start, int end) {
        int retries = 0;
        while (true) {
            try {
                return embedAndUpsert(batch);
            } catch (RuntimeException batchFailure) {
                if (!isRateLimited(batchFailure) || retries >= rateLimitMaxRetries) {
                    throw batchFailure;
                }
                retries++;
                log.warn(
                        "Vector batch rate limited. start={} end={} retry={}/{} delayMs={}",
                        start,
                        end,
                        retries,
                        rateLimitMaxRetries,
                        rateLimitRetryDelayMillis
                );
                pause(rateLimitRetryDelayMillis);
            }
        }
    }

    private int embedAndUpsert(List<Article> articles) {
        List<String> texts = articles.stream()
                .map(this::toIndexText)
                .toList();
        List<List<Double>> vectors = embeddingClient.embedAll(texts);
        if (vectors.size() != articles.size()) {
            throw new IllegalStateException("Embedding response size mismatch: expected "
                    + articles.size()
                    + ", actual "
                    + vectors.size());
        }

        List<VectorDocument> documents = new ArrayList<>();
        for (int i = 0; i < articles.size(); i++) {
            Article article = articles.get(i);
            documents.add(new VectorDocument(
                    String.valueOf(article.getId()),
                    vectors.get(i),
                metadata(article)
            ));
        }
        vectorSearchClient.upsert(collectionName, documents);
        return documents.size();
    }

    @Transactional(readOnly = true)
    public synchronized int ensureIndexed() {
        if (indexedCount.get() == 0) {
            return reindexAll();
        }
        return indexedCount.get();
    }

    public int indexedCount() {
        return indexedCount.get();
    }

    private String failureCode(RuntimeException exception) {
        if (exception instanceof OpenRouterClientException openRouterException) {
            return openRouterException.failureCode();
        }
        return "provider_request_failed";
    }

    private boolean isRateLimited(RuntimeException exception) {
        return exception instanceof OpenRouterClientException openRouterException
                && "provider_http_429".equals(openRouterException.failureCode());
    }

    private void pause(long delayMillis) {
        if (delayMillis == 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Vector indexing interrupted during provider backoff.", exception);
        }
    }

    public String toIndexText(Article article) {
        return String.join(" ",
                article.getLaw().getTitle(),
                article.getArticleNumber(),
                article.getArticleTitle(),
                article.getContent()
        );
    }

    private Map<String, Object> metadata(Article article) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("articleId", article.getId());
        metadata.put("lawId", article.getLaw().getId());
        metadata.put("lawTitle", article.getLaw().getTitle());
        metadata.put("articleNumber", article.getArticleNumber());
        metadata.put("articleTitle", article.getArticleTitle());
        return metadata;
    }
}
