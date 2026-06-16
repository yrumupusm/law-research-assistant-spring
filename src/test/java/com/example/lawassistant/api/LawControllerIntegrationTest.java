package com.example.lawassistant.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lawassistant.domain.entity.Article;
import com.example.lawassistant.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.llm.provider=mock",
        "app.embedding.provider=mock",
        "app.vector.provider=inmemory",
        "app.reranker.provider=mock",
        "app.reference-data.enabled=true"
})
@AutoConfigureMockMvc
class LawControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void lawListReturnsCountsAndPagingMetadata() throws Exception {
        mockMvc.perform(get("/api/laws")
                        .param("q", "대외무역법")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].title").value("대외무역법"))
                .andExpect(jsonPath("$.items[0].articleCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].revisionCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void v1LawListReturnsOriginalSnakeCaseResponseContract() throws Exception {
        mockMvc.perform(get("/api/v1/laws")
                        .param("q", "대외무역법")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].law_id").exists())
                .andExpect(jsonPath("$.items[0].law_type").value("law"))
                .andExpect(jsonPath("$.items[0].article_count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].revision_count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].articleCount").doesNotExist());
    }

    @Test
    void lawDetailReturnsCurrentArticles() throws Exception {
        Article current = articleRepository.searchByKeyword("전략물자를 수출하려는 경우").get(0);

        mockMvc.perform(get("/api/laws/{id}", current.getLaw().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawId").value(current.getLaw().getId()))
                .andExpect(jsonPath("$.title").value("대외무역법"))
                .andExpect(jsonPath("$.snapshotVersion").value("law-domain-2026-001"))
                .andExpect(jsonPath("$.effectiveBasis.snapshotVersion").value("law-domain-2026-001"))
                .andExpect(jsonPath("$.effectiveBasis.sourcePath").value("public-reference-data"))
                .andExpect(jsonPath("$.articles.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.articles[0].effectiveBasis.sourcePath").value("public-reference-data"))
                .andExpect(jsonPath("$.articles[*].articleId").value(hasItem(current.getId().intValue())));
    }

    @Test
    void articleDetailReturnsEffectiveBasis() throws Exception {
        Article current = articleRepository.searchByKeyword("전략물자를 수출하려는 경우").get(0);

        mockMvc.perform(get("/api/articles/{id}", current.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(current.getId()))
                .andExpect(jsonPath("$.lawTitle").value("대외무역법"))
                .andExpect(jsonPath("$.effectiveBasis.snapshotVersion").value("law-domain-2026-001"))
                .andExpect(jsonPath("$.effectiveBasis.sourcePath").value("public-reference-data"));
    }

    @Test
    void v1LawDetailAndArticleReturnOriginalSnakeCaseResponseContract() throws Exception {
        Article current = articleRepository.searchByKeyword("전략물자를 수출하려는 경우").get(0);

        mockMvc.perform(get("/api/v1/laws/{id}", current.getLaw().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.law_id").value(current.getLaw().getId()))
                .andExpect(jsonPath("$.law_type").value("law"))
                .andExpect(jsonPath("$.effective_basis.snapshot_version").value("law-domain-2026-001"))
                .andExpect(jsonPath("$.articles[0].article_id").exists())
                .andExpect(jsonPath("$.lawId").doesNotExist());

        mockMvc.perform(get("/api/v1/articles/{id}", current.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.article_id").value(current.getId()))
                .andExpect(jsonPath("$.law_title").value("대외무역법"))
                .andExpect(jsonPath("$.effective_basis.source_path").value("public-reference-data"))
                .andExpect(jsonPath("$.articleId").doesNotExist());
    }

    @Test
    void articleHistoryAndDiffExposeVersionChain() throws Exception {
        Article current = articleRepository.searchByKeyword("전략물자를 수출하려는 경우").get(0);

        mockMvc.perform(get("/api/articles/{id}/history", current.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawTitle").value("대외무역법"))
                .andExpect(jsonPath("$.articleNumber").value("제19조의2"))
                .andExpect(jsonPath("$.entries.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.entries[*].current").value(hasItem(true)))
                .andExpect(jsonPath("$.entries[*].amendmentKind").value(hasItem("개정")));

        mockMvc.perform(get("/api/articles/{id}/diff", current.getId())
                        .param("compareWith", current.getPreviousArticleId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawTitle").value("대외무역법"))
                .andExpect(jsonPath("$.articleNumber").value("제19조의2"))
                .andExpect(jsonPath("$.contentHashEqual").value(false))
                .andExpect(jsonPath("$.contentA").value(containsString("수출 허가 요건")))
                .andExpect(jsonPath("$.contentB").value(containsString("품목 분류와 목적지 국가")));
    }

    @Test
    void lawRevisionsExposeLawLevelRevisionGroups() throws Exception {
        Article current = articleRepository.searchByKeyword("전략물자를 수출하려는 경우").get(0);

        mockMvc.perform(get("/api/laws/{id}/revisions", current.getLaw().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawId").value(current.getLaw().getId()))
                .andExpect(jsonPath("$.lawTitle").value("대외무역법"))
                .andExpect(jsonPath("$.revisions.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.revisions[*].effectiveFrom").value(hasItem("2026-01-01")))
                .andExpect(jsonPath("$.revisions[*].effectiveFrom").value(hasItem("2024-01-01")));
    }

    @Test
    void askUsesAsOfDateForArticleRetrieval() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "전략물자 수출 허가 요건은 무엇인가요?",
                                  "asOf": "2025-06-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.citedArticles[*].content").value(hasItem(containsString("품목 분류와 목적지 국가"))));
    }
}
