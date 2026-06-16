package com.example.lawassistant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class ArticleRepositoryAsOfTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void searchByKeywordAsOfExcludesLegacyRowsWithoutEffectiveDates() {
        Law law = persistLaw();
        Article legacy = new Article(law, "제1조", "목적", "legacy 기준일 검색", 1);
        entityManager.persist(legacy);
        entityManager.flush();

        var withoutAsOf = articleRepository.searchByKeyword("legacy 기준일");
        var withAsOf = articleRepository.searchByKeywordAsOf("legacy 기준일", LocalDate.of(2026, 1, 1));

        assertThat(withoutAsOf).extracting(Article::getId).contains(legacy.getId());
        assertThat(withAsOf).isEmpty();
    }

    @Test
    void searchByKeywordAsOfIncludesEndDateBecauseSpringStoresInclusiveEffectiveTo() {
        Law law = persistLaw();
        Article article = new Article(
                law,
                "제2조",
                "기준일",
                "inclusive 종료일 검색",
                2,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                "개정",
                null
        );
        entityManager.persist(article);
        entityManager.flush();

        var result = articleRepository.searchByKeywordAsOf("inclusive 종료일", LocalDate.of(2025, 12, 31));

        assertThat(result).extracting(Article::getId).contains(article.getId());
    }

    private Law persistLaw() {
        SnapshotVersion snapshot = new SnapshotVersion(
                "as-of-test",
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                "test"
        );
        entityManager.persist(snapshot);
        Law law = new Law("law:as-of-test", "기준일테스트법", LawType.LAW, "LAW-ASOF", snapshot);
        entityManager.persist(law);
        return law;
    }
}
