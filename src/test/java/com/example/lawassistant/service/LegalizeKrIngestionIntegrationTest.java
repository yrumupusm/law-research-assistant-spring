package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.repository.LawRepository;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legalizeingestion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.llm.provider=mock",
        "app.embedding.provider=mock",
        "app.vector.provider=inmemory",
        "app.reranker.provider=mock",
        "app.reference-data.enabled=false",
        "app.ingestion.include-dirs=대외무역법,방위사업법,관세법,외국환거래법,국가첨단전략산업경쟁력강화및보호에관한특별조치법,산업기술의유출방지및보호에관한법률,국방과학기술혁신촉진법,군수품관리법",
        "app.ingestion.include-files=법률.md,시행령.md,시행규칙.md"
})
class LegalizeKrIngestionIntegrationTest {

    private static final Path SOURCE_ROOT = Path.of("C:/dev/legalize-kr/kr");

    @Autowired
    private LocalLawIngestionService localLawIngestionService;

    @Autowired
    private SnapshotVersionRepository snapshotVersionRepository;

    @Autowired
    private LawRepository lawRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void importsAllSelectedLawFilesAndRetainsSourceVersionWhenRepositoryIsAvailable() {
        assumeTrue(Files.isDirectory(SOURCE_ROOT), "legalize-kr repository is not available locally");

        var result = localLawIngestionService.ingest(SOURCE_ROOT, "legalize-contract", "fixture-commit");

        assertThat(result.filesProcessed()).isEqualTo(22);
        assertThat(result.filesFailed()).isZero();
        assertThat(result.lawsImported()).isEqualTo(22);
        assertThat(result.articlesImported()).isEqualTo(2053);
        assertThat(result.indexedArticles()).isEqualTo(2053);
        assertThat(lawRepository.count()).isEqualTo(22);
        assertThat(articleRepository.count()).isEqualTo(2053);

        var snapshot = snapshotVersionRepository.findFirstByStatusOrderByIndexedAtDesc(SnapshotStatus.INDEXED)
                .orElseThrow();
        assertThat(snapshot.getSourcePath()).isEqualTo(SOURCE_ROOT.toAbsolutePath().normalize().toString());
        assertThat(snapshot.getSourceVersion()).isEqualTo("fixture-commit");
    }
}
