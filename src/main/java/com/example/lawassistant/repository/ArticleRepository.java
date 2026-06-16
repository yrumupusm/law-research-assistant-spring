package com.example.lawassistant.repository;

import com.example.lawassistant.domain.entity.Article;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByLawIdAndEffectiveToIsNullOrderByOrderIndexAsc(Long lawId);

    long countByLawIdAndEffectiveToIsNull(Long lawId);

    List<Article> findByLawIdAndArticleNumberOrderByEffectiveFromAscIdAsc(Long lawId, String articleNumber);

    List<Article> findByLawIdOrderByEffectiveFromDescIdDesc(Long lawId);

    @Query("""
            select a
            from Article a
            join fetch a.law l
            join fetch l.snapshotVersion sv
            where l.slug = :lawSlug
              and a.articleNumber = :articleNumber
              and a.effectiveTo is null
            order by sv.indexedAt desc, a.id desc
            """)
    List<Article> findCurrentByLawSlugAndArticleNumber(
            @Param("lawSlug") String lawSlug,
            @Param("articleNumber") String articleNumber
    );

    @Query("""
            select a
            from Article a
            join fetch a.law l
            join fetch l.snapshotVersion sv
            where l.slug = :lawSlug
              and a.articleNumber = :articleNumber
            order by a.effectiveFrom asc, sv.indexedAt asc, a.id asc
            """)
    List<Article> findByLawSlugAndArticleNumberOrderByVersion(
            @Param("lawSlug") String lawSlug,
            @Param("articleNumber") String articleNumber
    );

    @Query("""
            select a
            from Article a
            join fetch a.law l
            where a.effectiveTo is null
              and (
                   lower(a.content) like lower(concat('%', :keyword, '%'))
                or lower(a.articleTitle) like lower(concat('%', :keyword, '%'))
                or lower(a.articleNumber) like lower(concat('%', :keyword, '%'))
                or lower(l.title) like lower(concat('%', :keyword, '%'))
              )
            order by l.title asc, a.orderIndex asc
            """)
    List<Article> searchByKeyword(@Param("keyword") String keyword);

    @Query("""
            select a
            from Article a
            join fetch a.law l
            where (a.effectiveFrom is not null or a.effectiveTo is not null)
              and (a.effectiveFrom is null or a.effectiveFrom <= :asOf)
              and (a.effectiveTo is null or a.effectiveTo >= :asOf)
              and (
                   lower(a.content) like lower(concat('%', :keyword, '%'))
                or lower(a.articleTitle) like lower(concat('%', :keyword, '%'))
                or lower(a.articleNumber) like lower(concat('%', :keyword, '%'))
                or lower(l.title) like lower(concat('%', :keyword, '%'))
              )
            order by l.title asc, a.orderIndex asc
            """)
    List<Article> searchByKeywordAsOf(@Param("keyword") String keyword, @Param("asOf") LocalDate asOf);
}
