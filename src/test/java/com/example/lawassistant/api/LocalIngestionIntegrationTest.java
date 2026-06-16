package com.example.lawassistant.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.repository.ArticleRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lawassistantingestion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.llm.provider=mock",
        "app.embedding.provider=mock",
        "app.vector.provider=inmemory",
        "app.reranker.provider=mock",
        "app.reference-data.enabled=true",
        "app.ingestion.include-dirs=",
        "app.ingestion.include-files="
})
@AutoConfigureMockMvc
class LocalIngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArticleRepository articleRepository;

    @TempDir
    private Path tempDir;

    @Test
    void ingestLocalMarkdownCreatesSnapshotLawsArticlesAndIndex() throws Exception {
        Path lawDir = tempDir.resolve("kr").resolve("테스트수출법");
        Files.createDirectories(lawDir);
        Files.writeString(lawDir.resolve("법률.md"), """
                ---
                제목: 테스트수출법
                법령구분: 법률
                법령MST: LAW-TEST-001
                공포일자: 2026-01-01
                시행일자: 2026-01-01
                상태: 시행
                ---

                ##### 제1조 (목적)
                이 법은 전략물자 수출 확인 절차를 정한다.

                ##### 제2조 (기술자료 제공)
                기술자료를 해외 기관에 제공하려는 경우 목적지 국가와 최종 사용자를 확인해야 한다.
                """, StandardCharsets.UTF_8);

        String sourceDir = tempDir.toString().replace("\\", "\\\\");

        mockMvc.perform(post("/api/admin/ingest-local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDir": "%s",
                                  "snapshotPrefix": "law-local-test"
                                }
                                """.formatted(sourceDir)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.snapshotVersion").value(startsWith("law-local-test-")))
                .andExpect(jsonPath("$.filesProcessed").value(1))
                .andExpect(jsonPath("$.filesFailed").value(0))
                .andExpect(jsonPath("$.lawsImported").value(1))
                .andExpect(jsonPath("$.articlesImported").value(2))
                .andExpect(jsonPath("$.indexedArticles").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/laws").param("keyword", "테스트수출법"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("테스트수출법"));

        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "테스트수출법에서 기술자료 제공 때 무엇을 확인해야 하나요?",
                                  "asOf": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.citedArticles[*].content").value(
                        hasItem(containsString("목적지 국가와 최종 사용자"))
                ));

        mockMvc.perform(get("/api/admin/ingestion-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].filesProcessed").value(1));

        Files.writeString(lawDir.resolve("법률.md"), """
                ---
                제목: 테스트수출법
                법령구분: 법률
                법령MST: LAW-TEST-001
                공포일자: 2026-01-01
                시행일자: 2026-02-01
                상태: 시행
                ---

                ##### 제1조 (목적)
                이 법은 전략물자 수출 확인 절차를 정한다.

                ##### 제2조 (기술자료 제공)
                기술자료를 해외 기관에 제공하려는 경우 목적지 국가, 최종 사용자, 추가 확인 절차를 확인해야 한다.
                """, StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/admin/ingest-local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceDir": "%s",
                                  "snapshotPrefix": "law-local-test"
                                }
                                """.formatted(sourceDir)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.filesProcessed").value(1))
                .andExpect(jsonPath("$.articlesImported").value(2));

        Article current = articleRepository.searchByKeyword("추가 확인 절차").get(0);

        mockMvc.perform(get("/api/articles/{id}/history", current.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawTitle").value("테스트수출법"))
                .andExpect(jsonPath("$.articleNumber").value("제2조"))
                .andExpect(jsonPath("$.entries.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.entries[*].current").value(hasItem(true)))
                .andExpect(jsonPath("$.entries[*].amendmentKind").value(hasItem("개정")));

        mockMvc.perform(get("/api/articles/{id}/diff", current.getId())
                        .param("compareWith", current.getPreviousArticleId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHashEqual").value(false))
                .andExpect(jsonPath("$.contentA").value(containsString("추가 확인 절차")))
                .andExpect(jsonPath("$.contentB").value(containsString("목적지 국가와 최종 사용자")));
    }
}
