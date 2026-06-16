package com.example.lawassistant.infrastructure.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.lawassistant.infrastructure.llm.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenRouterChatModelClientTest {

    @Test
    void generateJsonCallsOpenRouterChatCompletionEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatModelClient client = new OpenRouterChatModelClient(
                builder,
                new ObjectMapper(),
                "https://openrouter.ai/api/v1",
                "test-key",
                "test/model",
                0.1,
                500,
                5
        );

        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("test/model"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "{\\"status\\":\\"OK\\",\\"answer\\":\\"ok-value\\"}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.generateJson(List.of(new ChatMessage("user", "hello")), "AnswerSchema");

        assertThat(result).containsEntry("status", "OK");
        assertThat(result).containsEntry("answer", "ok-value");
        server.verify();
    }

    @Test
    void generateJsonParsesFencedJsonContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatModelClient client = client(builder);

        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "```json\\n{\\"reasoning\\":\\"근거 조문을 확인했습니다.\\",\\"confidence\\":0.7}\\n```"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.generateJson(List.of(new ChatMessage("user", "hello")), "AnswerDraft");

        assertThat(result).containsEntry("reasoning", "근거 조문을 확인했습니다.");
        assertThat(result).containsEntry("confidence", 0.7);
        server.verify();
    }

    @Test
    void generateJsonExtractsFirstJsonObjectFromTextContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatModelClient client = client(builder);

        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "아래 JSON을 사용하세요. {\\"reasoning\\":\\"한국어 답변입니다.\\",\\"followUpQuestions\\":[\\"목적지 국가를 확인해 주세요.\\"],\\"confidence\\":0.82} 감사합니다."
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = client.generateJson(List.of(new ChatMessage("user", "hello")), "AnswerDraft");

        assertThat(result).containsEntry("reasoning", "한국어 답변입니다.");
        assertThat(result).containsEntry("confidence", 0.82);
        assertThat(result.get("followUpQuestions").toString()).contains("목적지 국가를 확인해 주세요.");
        server.verify();
    }

    @Test
    void generateJsonRejectsTextWithoutJsonObject() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterChatModelClient client = client(builder);

        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "근거 조문을 확인했습니다."
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateJson(List.of(new ChatMessage("user", "hello")), "AnswerDraft"))
                .isInstanceOf(OpenRouterResponseFormatException.class)
                .hasMessageContaining("not valid JSON")
                .hasMessageNotContaining("근거 조문");
        server.verify();
    }

    private OpenRouterChatModelClient client(RestClient.Builder builder) {
        return new OpenRouterChatModelClient(
                builder,
                new ObjectMapper(),
                "https://openrouter.ai/api/v1",
                "test-key",
                "test/model",
                0.1,
                500,
                5
        );
    }
}
