package com.example.lawassistant.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class QdrantVectorSearchClientTest {

    @Test
    void upsertCreatesCollectionWhenMissingAndWritesPoints() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        QdrantVectorSearchClient client = new QdrantVectorSearchClient(
                builder,
                "http://localhost:6333",
                "qdrant-key",
                "Cosine"
        );

        server.expect(requestTo("http://localhost:6333/collections/law_articles"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("api-key", "qdrant-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://localhost:6333/collections/law_articles"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.vectors.size").value(3))
                .andExpect(jsonPath("$.vectors.distance").value("Cosine"))
                .andRespond(withSuccess("{\"result\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:6333/collections/law_articles/points?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.points[0].id").value(10))
                .andExpect(jsonPath("$.points[0].vector[0]").value(0.1))
                .andExpect(jsonPath("$.points[0].payload.lawTitle").value("Foreign Trade Act"))
                .andRespond(withSuccess("{\"result\":{\"operation_id\":1}}", MediaType.APPLICATION_JSON));

        client.upsert("law_articles", List.of(new VectorDocument(
                "10",
                List.of(0.1, 0.2, 0.3),
                Map.of("lawTitle", "Foreign Trade Act")
        )));

        server.verify();
    }

    @Test
    void upsertUsesExistingCollectionAndWritesPoints() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        QdrantVectorSearchClient client = new QdrantVectorSearchClient(
                builder,
                "http://localhost:6333",
                "",
                "Cosine"
        );

        server.expect(requestTo("http://localhost:6333/collections/law_articles"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"result\":{\"status\":\"green\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost:6333/collections/law_articles/points?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.points[0].id").value(10))
                .andRespond(withSuccess("{\"result\":{\"operation_id\":1}}", MediaType.APPLICATION_JSON));

        client.upsert("law_articles", List.of(new VectorDocument(
                "10",
                List.of(0.1, 0.2, 0.3),
                Map.of("lawTitle", "Foreign Trade Act")
        )));

        server.verify();
    }

    @Test
    void upsertToleratesCollectionAlreadyCreatedByAnotherRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        QdrantVectorSearchClient client = new QdrantVectorSearchClient(
                builder,
                "http://localhost:6333",
                "",
                "Cosine"
        );

        server.expect(requestTo("http://localhost:6333/collections/law_articles"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://localhost:6333/collections/law_articles"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo("http://localhost:6333/collections/law_articles/points?wait=true"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.points[0].id").value(10))
                .andRespond(withSuccess("{\"result\":{\"operation_id\":1}}", MediaType.APPLICATION_JSON));

        client.upsert("law_articles", List.of(new VectorDocument(
                "10",
                List.of(0.1, 0.2, 0.3),
                Map.of("lawTitle", "Foreign Trade Act")
        )));

        server.verify();
    }

    @Test
    void searchMapsQdrantResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        QdrantVectorSearchClient client = new QdrantVectorSearchClient(
                builder,
                "http://localhost:6333",
                "",
                "Cosine"
        );

        server.expect(requestTo("http://localhost:6333/collections/law_articles/points/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.vector[1]").value(0.2))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.with_payload").value(true))
                .andRespond(withSuccess("""
                        {
                          "result": [
                            {
                              "id": 10,
                              "score": 0.91,
                              "payload": {
                                "articleId": 10,
                                "lawTitle": "Foreign Trade Act"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<VectorSearchResult> results = client.search("law_articles", List.of(0.1, 0.2, 0.3), 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("10");
        assertThat(results.get(0).score()).isEqualTo(0.91);
        assertThat(results.get(0).metadata()).containsEntry("lawTitle", "Foreign Trade Act");
        assertThat(results.get(0).metadata()).containsEntry("articleId", 10);
        server.verify();
    }

    @Test
    void searchFailsWhenQdrantResponseDoesNotContainResultArray() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        QdrantVectorSearchClient client = new QdrantVectorSearchClient(
                builder,
                "http://localhost:6333",
                "",
                "Cosine"
        );

        server.expect(requestTo("http://localhost:6333/collections/law_articles/points/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "status": "ok",
                          "result": {}
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search("law_articles", List.of(0.1, 0.2, 0.3), 2))
                .isInstanceOf(QdrantVectorClientException.class)
                .hasMessageContaining("result array");
        server.verify();
    }
}
