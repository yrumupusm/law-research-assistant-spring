package com.example.lawassistant.api;

import static org.hamcrest.Matchers.containsString;
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
        "app.admin.reindex-enabled=false"
})
@AutoConfigureMockMvc
class AdminReindexDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminStatusShowsReindexDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reindexEnabled").value(false));
    }

    @Test
    void reindexIsForbiddenWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/admin/reindex"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("ADMIN_REINDEX_ENABLED=true")));
    }

    @Test
    void v1ReindexIsForbiddenWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/admin/reindex"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("ADMIN_REINDEX_ENABLED=true")));
    }
}
