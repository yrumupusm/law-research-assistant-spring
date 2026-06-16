package com.example.lawassistant.infrastructure.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CohereRerankerClientTest {

    @Test
    void rerankCallsCohereEndpointAndMapsResultOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CohereRerankerClient client = new CohereRerankerClient(
                builder,
                "https://api.cohere.com",
                "test-key",
                "rerank-v3.5"
        );

        server.expect(requestTo("https://api.cohere.com/v2/rerank"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("rerank-v3.5"))
                .andExpect(jsonPath("$.query").value("무역안보관리원 법적 근거"))
                .andExpect(jsonPath("$.documents[0]").value("낮은 관련도"))
                .andExpect(jsonPath("$.documents[1]").value("높은 관련도"))
                .andExpect(jsonPath("$.top_n").value(2))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {"index": 1, "relevance_score": 0.98},
                            {"index": 0, "relevance_score": 0.12}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.rerank(
                "무역안보관리원 법적 근거",
                List.of(
                        new RerankCandidate("low", "낮은 관련도", 0.7, Map.of("articleNumber", "제19조")),
                        new RerankCandidate("high", "높은 관련도", 0.2, Map.of("articleNumber", "제25조"))
                ),
                2
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("high");
        assertThat(result.get(0).score()).isEqualTo(0.98);
        assertThat(result.get(1).id()).isEqualTo("low");
        server.verify();
    }
}
