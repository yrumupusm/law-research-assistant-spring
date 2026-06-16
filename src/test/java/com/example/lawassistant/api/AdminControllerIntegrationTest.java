package com.example.lawassistant.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.llm.provider=mock",
        "app.embedding.provider=mock",
        "app.vector.provider=inmemory",
        "app.reranker.provider=mock",
        "app.reference-data.enabled=true",
        "app.admin.reindex-enabled=true"
})
@AutoConfigureMockMvc
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminStatusReturnsSeededIndexStatus() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexStatus").value("healthy"))
                .andExpect(jsonPath("$.lawsCount").value(8))
                .andExpect(jsonPath("$.articlesCount").value(21))
                .andExpect(jsonPath("$.indexedArticlesCount").value(21))
                .andExpect(jsonPath("$.unindexedArticlesCount").value(0))
                .andExpect(jsonPath("$.recentFailures.length()").value(0))
                .andExpect(jsonPath("$.reindexEnabled").value(true))
                .andExpect(jsonPath("$.syncState").doesNotExist());
    }

    @Test
    void v1AdminStatusReturnsOriginalSnakeCaseResponseContract() throws Exception {
        mockMvc.perform(get("/api/v1/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.index_status").value("healthy"))
                .andExpect(jsonPath("$.laws_count").value(8))
                .andExpect(jsonPath("$.articles_count").value(21))
                .andExpect(jsonPath("$.indexed_articles_count").value(21))
                .andExpect(jsonPath("$.unindexed_articles_count").value(0))
                .andExpect(jsonPath("$.recent_failures.length()").value(0))
                .andExpect(jsonPath("$.reindex_enabled").value(true))
                .andExpect(jsonPath("$.indexStatus").doesNotExist());
    }

    @Test
    void providerSmokeTestUsesConfiguredProviders() throws Exception {
        mockMvc.perform(post("/api/admin/provider-smoke-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmProvider").value("mock"))
                .andExpect(jsonPath("$.embeddingProvider").value("mock"))
                .andExpect(jsonPath("$.rerankerProvider").value("mock"))
                .andExpect(jsonPath("$.llmStatus").value("ok"))
                .andExpect(jsonPath("$.embeddingStatus").value("ok"))
                .andExpect(jsonPath("$.rerankerStatus").value("ok"))
                .andExpect(jsonPath("$.embeddingDimensions").value(8))
                .andExpect(jsonPath("$.rerankedCount").value(1))
                .andExpect(jsonPath("$.topRerankedId").value("a"));
    }

    @Test
    void v1ReindexReturnsAcceptedAndSchedulesRun() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.message").value("reindex scheduled"))
                .andExpect(jsonPath("$.ingestion_run_id").exists())
                .andExpect(jsonPath("$.snapshot_version").value("law-domain-2026-001"));
    }

    @Test
    void v1ProviderSmokeTestReturnsOriginalSnakeCaseResponseContract() throws Exception {
        mockMvc.perform(post("/api/v1/admin/provider-smoke-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llm_provider").value("mock"))
                .andExpect(jsonPath("$.embedding_provider").value("mock"))
                .andExpect(jsonPath("$.reranker_provider").value("mock"))
                .andExpect(jsonPath("$.embedding_dimensions").value(8))
                .andExpect(jsonPath("$.top_reranked_id").value("a"))
                .andExpect(jsonPath("$.llmProvider").doesNotExist());
    }

    @Test
    void reindexRecordsIngestionRun() throws Exception {
        mockMvc.perform(post("/api/admin/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.indexedArticles").value(21))
                .andExpect(jsonPath("$.failedArticles").value(0))
                .andExpect(jsonPath("$.snapshotVersion").value("law-domain-2026-001"));

        mockMvc.perform(get("/api/admin/ingestion-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].filesProcessed").value(21));
    }
}
