package com.example.lawassistant.infrastructure.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenRouterEmbeddingClientTest {

    @Test
    void embedAllCallsOpenRouterEmbeddingEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterEmbeddingClient client = new OpenRouterEmbeddingClient(
                builder,
                "https://openrouter.ai/api/v1",
                "test-key",
                "baai/bge-m3",
                3,
                4000,
                5
        );

        server.expect(requestTo("https://openrouter.ai/api/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("baai/bge-m3"))
                .andExpect(jsonPath("$.input[0]").value("alpha"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"embedding": [0.1, 0.2, 0.3]},
                            {"embedding": [0.4, 0.5, 0.6]}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.embedAll(List.of("alpha", "beta"));

        assertThat(result).containsExactly(
                List.of(0.1, 0.2, 0.3),
                List.of(0.4, 0.5, 0.6)
        );
        server.verify();
    }

    @Test
    void embedAllTruncatesLongInputsBeforeRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterEmbeddingClient client = new OpenRouterEmbeddingClient(
                builder,
                "https://openrouter.ai/api/v1",
                "test-key",
                "baai/bge-m3",
                3,
                5,
                5
        );

        server.expect(requestTo("https://openrouter.ai/api/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.input[0]").value("abcde"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"embedding": [0.1, 0.2, 0.3]}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.embedAll(List.of("abcdefghij"));

        assertThat(result).containsExactly(List.of(0.1, 0.2, 0.3));
        server.verify();
    }

    @Test
    void embedAllFailsWhenEmbeddingDimensionDoesNotMatchConfiguration() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterEmbeddingClient client = new OpenRouterEmbeddingClient(
                builder,
                "https://openrouter.ai/api/v1",
                "test-key",
                "baai/bge-m3",
                3,
                4000,
                5
        );

        server.expect(requestTo("https://openrouter.ai/api/v1/embeddings"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"embedding": [0.1, 0.2]}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embedAll(List.of("alpha")))
                .isInstanceOf(OpenRouterClientException.class)
                .hasMessageContaining("dimension mismatch");
        server.verify();
    }

    @Test
    void embedAllFailsWhenOpenRouterReturnsEmptyData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterEmbeddingClient client = new OpenRouterEmbeddingClient(
                builder,
                "https://openrouter.ai/api/v1",
                "test-key",
                "baai/bge-m3",
                3,
                4000,
                5
        );

        server.expect(requestTo("https://openrouter.ai/api/v1/embeddings"))
                .andRespond(withSuccess("""
                        {
                          "data": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embedAll(List.of("alpha")))
                .isInstanceOf(OpenRouterClientException.class)
                .hasMessageContaining("did not contain data");
        server.verify();
    }
}
