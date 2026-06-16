package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.dto.ArticleDiffResponse;
import com.example.lawassistant.dto.ArticleHistoryEntryDto;
import com.example.lawassistant.dto.ArticleHistoryResponse;
import com.example.lawassistant.dto.ArticleResponse;
import com.example.lawassistant.dto.EffectiveBasisDto;
import com.example.lawassistant.dto.LawDetailResponse;
import com.example.lawassistant.dto.LawListResponse;
import com.example.lawassistant.dto.LawRevisionDto;
import com.example.lawassistant.dto.LawRevisionListResponse;
import com.example.lawassistant.dto.LawSummaryDto;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.repository.LawRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LawQueryService {

    private final LawRepository lawRepository;
    private final ArticleRepository articleRepository;

    public LawQueryService(LawRepository lawRepository, ArticleRepository articleRepository) {
        this.lawRepository = lawRepository;
        this.articleRepository = articleRepository;
    }

    @Transactional(readOnly = true)
    public LawListResponse findLaws(String keyword) {
        return findLaws(keyword, 1, 20);
    }

    @Transactional(readOnly = true)
    public LawListResponse findLaws(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        List<Law> laws = keyword == null || keyword.isBlank()
                ? lawRepository.findAll()
                : lawRepository.findByTitleContainingIgnoreCaseOrderByTitleAsc(keyword);
        laws = laws.stream()
                .sorted(Comparator.comparing(Law::getTitle).thenComparing(Law::getId))
                .toList();
        int fromIndex = Math.min((safePage - 1) * safeSize, laws.size());
        int toIndex = Math.min(fromIndex + safeSize, laws.size());
        List<Law> pageItems = laws.subList(fromIndex, toIndex);
        return new LawListResponse(
                laws.size(),
                safePage,
                safeSize,
                pageItems.stream()
                        .map(law -> new LawSummaryDto(
                                law.getId(),
                                law.getTitle(),
                                law.getLawType(),
                                law.getLawNumber(),
                                articleRepository.countByLawIdAndEffectiveToIsNull(law.getId()),
                                countRevisionGroups(law.getId())
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ArticleResponse findArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("article not found: " + id));
        return toArticleResponse(article);
    }

    @Transactional(readOnly = true)
    public LawDetailResponse findLaw(Long id) {
        Law law = lawRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("law not found: " + id));
        List<ArticleResponse> articles = articleRepository.findByLawIdAndEffectiveToIsNullOrderByOrderIndexAsc(id)
                .stream()
                .map(this::toArticleResponse)
                .toList();
        return new LawDetailResponse(
                law.getId(),
                law.getSlug(),
                law.getTitle(),
                law.getLawType(),
                law.getLawNumber(),
                law.getEnactedAt(),
                law.getLastAmendedAt(),
                law.getEffectiveFrom(),
                law.getEffectiveTo(),
                law.getSnapshotVersion().getVersion(),
                articles,
                effectiveBasis(law)
        );
    }

    @Transactional(readOnly = true)
    public LawRevisionListResponse findLawRevisions(Long id) {
        Law law = lawRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("law not found: " + id));
        Map<RevisionKey, Long> grouped = new LinkedHashMap<>();
        articleRepository.findByLawIdOrderByEffectiveFromDescIdDesc(id).forEach(article -> {
            RevisionKey key = new RevisionKey(article.getEffectiveFrom(), article.getAmendmentKind());
            grouped.put(key, grouped.getOrDefault(key, 0L) + 1);
        });
        List<LawRevisionDto> revisions = grouped.entrySet().stream()
                .map(entry -> new LawRevisionDto(
                        entry.getKey().effectiveFrom(),
                        entry.getKey().amendmentKind(),
                        entry.getValue()
                ))
                .sorted(Comparator
                        .comparing(LawRevisionDto::effectiveFrom, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(dto -> dto.amendmentKind() == null ? "" : dto.amendmentKind()))
                .toList();
        return new LawRevisionListResponse(law.getId(), law.getTitle(), revisions);
    }

    private long countRevisionGroups(Long lawId) {
        return articleRepository.findByLawIdOrderByEffectiveFromDescIdDesc(lawId).stream()
                .map(article -> new RevisionKey(article.getEffectiveFrom(), article.getAmendmentKind()))
                .distinct()
                .count();
    }

    @Transactional(readOnly = true)
    public ArticleHistoryResponse findArticleHistory(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("article not found: " + id));
        List<Article> entries = articleRepository.findByLawSlugAndArticleNumberOrderByVersion(
                article.getLaw().getSlug(),
                article.getArticleNumber()
        );
        return new ArticleHistoryResponse(
                article.getLaw().getId(),
                article.getLaw().getTitle(),
                article.getArticleNumber(),
                entries.stream().map(this::toHistoryEntry).toList()
        );
    }

    @Transactional(readOnly = true)
    public ArticleDiffResponse compareArticles(Long articleId, Long compareWith) {
        if (articleId.equals(compareWith)) {
            throw new IllegalArgumentException("articleId and compareWith must be different");
        }
        Article a = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("article not found: " + articleId));
        Article b = articleRepository.findById(compareWith)
                .orElseThrow(() -> new IllegalArgumentException("article not found: " + compareWith));
        if (!a.getLaw().getSlug().equals(b.getLaw().getSlug())) {
            throw new IllegalArgumentException("articles must belong to the same law");
        }
        if (!a.getArticleNumber().equals(b.getArticleNumber())) {
            throw new IllegalArgumentException("articles must have the same article number");
        }
        return new ArticleDiffResponse(
                a.getId(),
                b.getId(),
                a.getLaw().getId(),
                a.getLaw().getTitle(),
                a.getArticleNumber(),
                a.getContent(),
                b.getContent(),
                a.getContentHash(),
                b.getContentHash(),
                a.getContentHash() != null && a.getContentHash().equals(b.getContentHash())
        );
    }

    private ArticleHistoryEntryDto toHistoryEntry(Article article) {
        return new ArticleHistoryEntryDto(
                article.getId(),
                article.getArticleNumber(),
                article.getArticleTitle(),
                article.getContent(),
                article.getContentHash(),
                article.getEffectiveFrom(),
                article.getEffectiveTo(),
                article.getAmendmentKind(),
                article.getPreviousArticleId(),
                article.getEffectiveTo() == null
        );
    }

    private ArticleResponse toArticleResponse(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getLaw().getId(),
                article.getLaw().getTitle(),
                article.getArticleNumber(),
                article.getArticleTitle(),
                article.getContent(),
                article.getContentHash(),
                article.getEffectiveFrom(),
                article.getEffectiveTo(),
                article.getAmendmentKind(),
                article.getPreviousArticleId(),
                effectiveBasis(article.getLaw())
        );
    }

    private EffectiveBasisDto effectiveBasis(Law law) {
        return new EffectiveBasisDto(
                law.getSnapshotVersion().getVersion(),
                law.getSnapshotVersion().getIndexedAt(),
                law.getSnapshotVersion().getSourcePath(),
                null
        );
    }

    private record RevisionKey(LocalDate effectiveFrom, String amendmentKind) {
    }
}
