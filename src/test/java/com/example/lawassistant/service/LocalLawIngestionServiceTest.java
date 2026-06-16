package com.example.lawassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lawassistant.domain.entity.IngestionRun;
import com.example.lawassistant.domain.enums.IngestionStatus;
import com.example.lawassistant.repository.ArticleRepository;
import com.example.lawassistant.repository.IngestionRunRepository;
import com.example.lawassistant.repository.LawRepository;
import com.example.lawassistant.repository.SnapshotVersionRepository;
import com.example.lawassistant.service.model.VectorIndexResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalLawIngestionServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void ingestMarksRunPartialFailedWhenAFileThrowsDuringParsing() throws Exception {
        Files.writeString(tempDir.resolve("broken.md"), "broken", StandardCharsets.UTF_8);

        MarkdownLawParser parser = mock(MarkdownLawParser.class);
        when(parser.parse(anyString(), anyString())).thenThrow(new IllegalArgumentException("parse failed"));

        SnapshotVersionRepository snapshotRepository = mock(SnapshotVersionRepository.class);
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IngestionRunRepository runRepository = mock(IngestionRunRepository.class);
        when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VectorIndexService vectorIndexService = mock(VectorIndexService.class);
        when(vectorIndexService.reindexAllDetailed()).thenReturn(new VectorIndexResult(0, 0, List.of()));

        LocalLawIngestionService service = new LocalLawIngestionService(
                parser,
                snapshotRepository,
                mock(LawRepository.class),
                mock(ArticleRepository.class),
                runRepository,
                vectorIndexService,
                null,
                "law-test",
                "",
                ""
        );

        var result = service.ingest(tempDir, "law-test");
        IngestionRun run = result.run();

        assertThat(run.getStatus()).isEqualTo(IngestionStatus.PARTIAL_FAILED);
        assertThat(run.getFilesProcessed()).isZero();
        assertThat(run.getFilesFailed()).isEqualTo(1);
        assertThat(run.getErrorMessage()).contains("broken.md").contains("IllegalArgumentException");
        assertThat(result.filesFailed()).isEqualTo(1);
        assertThat(result.indexedArticles()).isZero();
    }
}
