package com.example.lawassistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpRequestAuditFilterTest {

    private final HttpRequestAuditFilter filter = new HttpRequestAuditFilter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestPayloadContainsOperationalFieldsOnly() throws Exception {
        String payload = filter.requestPayload("POST", "/api/ask", 200, 17);

        var json = objectMapper.readTree(payload);
        assertThat(json.path("method").asText()).isEqualTo("POST");
        assertThat(json.path("path").asText()).isEqualTo("/api/ask");
        assertThat(json.path("status").asInt()).isEqualTo(200);
        assertThat(json.path("elapsedMs").asLong()).isEqualTo(17L);
        assertThat(payload)
                .doesNotContain("question")
                .doesNotContain("api_key")
                .doesNotContain("sk-secret");
    }

    @Test
    void safePathExcludesQueryString() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/laws");
        request.setQueryString("api_key=sk-secret&question=secret");

        assertThat(filter.safePath(request)).isEqualTo("/api/laws");
        assertThat(filter.safePath(request))
                .doesNotContain("sk-secret")
                .doesNotContain("question");
    }

    @Test
    void filterPropagatesDownstreamErrorAfterAuditPathRuns() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/broken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, (servletRequest, servletResponse) -> {
            ((MockHttpServletResponse) servletResponse).setStatus(500);
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(response.getStatus()).isEqualTo(500);
    }
}
