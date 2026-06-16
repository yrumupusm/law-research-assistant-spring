package com.example.lawassistant.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "app.reference-data.enabled=true"
})
@AutoConfigureMockMvc
class HealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsDatabaseAndIndexChecks() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.checks.db").value("ok"))
                .andExpect(jsonPath("$.checks.index.status").value("healthy"))
                .andExpect(jsonPath("$.checks.index.articles").value(21))
                .andExpect(jsonPath("$.checks.index.indexedArticles").value(21))
                .andExpect(jsonPath("$.checks.index.unindexedArticles").value(0));
    }

    @Test
    void rootHealthzReturnsOk() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
