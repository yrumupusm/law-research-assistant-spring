package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.domain.entity.IngestionRun;
import com.example.lawassistant.domain.entity.Law;
import com.example.lawassistant.domain.entity.SnapshotVersion;
import com.example.lawassistant.domain.enums.SnapshotStatus;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.repository.IngestionRunRepository;
import com.example.lawassistant.repository.LawRepository;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import com.example.lawassistant.service.model.LocalIngestionResult;
import com.example.lawassistant.service.model.ParsedLawFile;
import com.example.lawassistant.service.model.VectorIndexResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalLawIngestionService {

    private static final DateTimeFormatter SNAPSHOT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSSSSS");

    private final MarkdownLawParser markdownLawParser;
    private final SnapshotVersionRepository snapshotVersionRepository;
    private final LawRepository lawRepository;
    private final ArticleRepository articleRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final VectorIndexService vectorIndexService;
    private final Path defaultSourceDir;
    private final String defaultSnapshotPrefix;
    private final Set<String> includeDirs;
    private final Set<String> includeFiles;

    public LocalLawIngestionService(
            MarkdownLawParser markdownLawParser,
            SnapshotVersionRepository snapshotVersionRepository,
            LawRepository lawRepository,
            ArticleRepository articleRepository,
            IngestionRunRepository ingestionRunRepository,
            VectorIndexService vectorIndexService,
            @Value("${app.ingestion.source-dir:}") String defaultSourceDir,
            @Value("${app.rag.snapshot-prefix:law}") String defaultSnapshotPrefix,
            @Value("${app.ingestion.include-dirs:}") String includeDirs,
            @Value("${app.ingestion.include-files:}") String includeFiles
    ) {
        this.markdownLawParser = markdownLawParser;
        this.snapshotVersionRepository = snapshotVersionRepository;
        this.lawRepository = lawRepository;
        this.articleRepository = articleRepository;
        this.ingestionRunRepository = ingestionRunRepository;
        this.vectorIndexService = vectorIndexService;
        this.defaultSourceDir = defaultSourceDir == null || defaultSourceDir.isBlank() ? null : Path.of(defaultSourceDir);
        this.defaultSnapshotPrefix = defaultSnapshotPrefix;
        this.includeDirs = csvSet(includeDirs);
        this.includeFiles = csvSet(includeFiles);
    }

    @Transactional
    public LocalIngestionResult ingest(Path requestedSourceDir, String requestedSnapshotPrefix) {
        Path sourceDir = requestedSourceDir != null ? requestedSourceDir : defaultSourceDir;
        if (sourceDir == null) {
            throw new IllegalArgumentException("sourceDir is required when app.ingestion.source-dir is not configured.");
        }
        Path root = sourceDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("sourceDir does not exist or is not a directory: " + root);
        }

        SnapshotVersion snapshot = snapshotVersionRepository.save(new SnapshotVersion(
                buildSnapshotVersion(requestedSnapshotPrefix),
                SnapshotStatus.INDEXED,
                LocalDateTime.now(),
                root.toString()
        ));
        IngestionRun run = ingestionRunRepository.save(IngestionRun.start(snapshot));

        int filesProcessed = 0;
        int filesFailed = 0;
        int lawsImported = 0;
        int articlesImported = 0;
        List<String> failedFiles = new ArrayList<>();

        for (Path file : markdownFiles(root)) {
            try {
                String sourcePath = root.relativize(file).toString().replace('\\', '/');
                ParsedLawFile parsed = markdownLawParser.parse(Files.readString(file, StandardCharsets.UTF_8), sourcePath);
                if (parsed == null) {
                    continue;
                }
                Law law = new Law(
                        buildSlug(parsed.title(), parsed.lawType().name()),
                        parsed.title(),
                        parsed.lawType(),
                        parsed.lawNumber(),
                        snapshot
                );
                law.applyMetadata(
                        parsed.enactedAt(),
                        parsed.lastAmendedAt(),
                        parsed.effectiveFrom(),
                        parsed.effectiveTo(),
                        parsed.sourcePath(),
                        null
                );
                Law savedLaw = lawRepository.save(law);
                parsed.articles().forEach(article -> {
                    Article previous = findPreviousCurrent(savedLaw.getSlug(), article.articleNumber());
                    Long previousArticleId = previous == null ? null : previous.getId();
                    Article next = new Article(
                            savedLaw,
                            article.articleNumber(),
                            article.articleTitle(),
                            article.content(),
                            article.orderIndex(),
                            parsed.effectiveFrom(),
                            parsed.effectiveTo(),
                            amendmentKind(previous, article.content()),
                            previousArticleId
                    );
                    if (previous != null) {
                        previous.closeAt(previousEffectiveTo(next.getEffectiveFrom()));
                        articleRepository.save(previous);
                    }
                    articleRepository.save(next);
                });
                filesProcessed++;
                lawsImported++;
                articlesImported += parsed.articles().size();
            } catch (RuntimeException | IOException ex) {
                filesFailed++;
                failedFiles.add(root.relativize(file).toString().replace('\\', '/') + ": " + ex.getClass().getSimpleName());
            }
        }

        VectorIndexResult indexResult = vectorIndexService.reindexAllDetailed();
        int indexed = indexResult.indexedArticles();
        int totalFailures = filesFailed + indexResult.failedArticles();
        String failureSummary = combineFailures(summarizeFailures(failedFiles), indexResult.failureSummary());
        run.complete(filesProcessed, totalFailures, failureSummary);
        ingestionRunRepository.save(run);

        return new LocalIngestionResult(
                run,
                snapshot.getVersion(),
                filesProcessed,
                totalFailures,
                lawsImported,
                articlesImported,
                indexed
        );
    }

    private List<Path> markdownFiles(Path root) {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> included(root, path))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read sourceDir: " + root, ex);
        }
    }

    private boolean included(Path root, Path file) {
        Path relative = root.relativize(file);
        if (!includeFiles.isEmpty() && !includeFiles.contains(file.getFileName().toString())) {
            return false;
        }
        if (includeDirs.isEmpty()) {
            return true;
        }
        for (Path segment : relative) {
            if (includeDirs.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> csvSet(String raw) {
        Set<String> result = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String token : raw.split(",")) {
            String value = token.strip();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private String buildSnapshotVersion(String requestedSnapshotPrefix) {
        String prefix = requestedSnapshotPrefix == null || requestedSnapshotPrefix.isBlank()
                ? defaultSnapshotPrefix
                : requestedSnapshotPrefix.strip();
        return prefix + "-" + LocalDateTime.now().format(SNAPSHOT_TIME);
    }

    private String buildSlug(String title, String lawType) {
        return lawType.toLowerCase() + ":" + title;
    }

    private Article findPreviousCurrent(String lawSlug, String articleNumber) {
        return articleRepository.findCurrentByLawSlugAndArticleNumber(lawSlug, articleNumber)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String amendmentKind(Article previous, String nextContent) {
        if (previous == null) {
            return "수집";
        }
        Article probe = new Article(previous.getLaw(), previous.getArticleNumber(), previous.getArticleTitle(), nextContent, previous.getOrderIndex());
        return previous.getContentHash().equals(probe.getContentHash()) ? "유지" : "개정";
    }

    private LocalDate previousEffectiveTo(LocalDate nextEffectiveFrom) {
        LocalDate basis = nextEffectiveFrom == null ? LocalDate.now() : nextEffectiveFrom;
        return basis.minusDays(1);
    }

    private String summarizeFailures(List<String> failedFiles) {
        if (failedFiles.isEmpty()) {
            return null;
        }
        String joined = String.join("; ", failedFiles);
        if (joined.length() <= 1800) {
            return joined;
        }
        return joined.substring(0, 1797) + "...";
    }

    private String combineFailures(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        String combined = first + "; " + second;
        if (combined.length() <= 1800) {
            return combined;
        }
        return combined.substring(0, 1797) + "...";
    }
}
