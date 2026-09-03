package com.example.lawassistant.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaticResourceTextTest {

    private static final Path STATIC_DIR = Path.of("src/main/resources/static");
    private static final List<String> MOJIBAKE_MARKERS = List.of(
            "\u8E30", "\uF9DE", "\u6D39", "\u8B70", "\u5BC3", "\u5AC4", "\uCA0C", "\uFFFD"
    );
    private static final List<String> FORBIDDEN_COPY = List.of(
            "demo", "sample", "mini project", "\uD3EC\uD2B8\uD3F4\uB9AC\uC624", "\uBBF8\uB2C8 \uD504\uB85C\uC81D\uD2B8",
            "\uC2E4\uC81C \uD68C\uC0AC \uB370\uC774\uD130", "This response", "sample articles", "demo project",
            "\uC810\uC218", "confidence", "\uAC80\uC99D\uC810\uC218"
    );

    @Test
    void staticPagesKeepReadableKoreanCopy() throws IOException {
        String index = read("index.html");
        String admin = read("admin.html");

        assertThat(index).contains("<title>\uBC95\uB839 \uAC80\uC0C9 \uC5B4\uC2DC\uC2A4\uD134\uD2B8</title>");
        assertThat(index).contains("\uC804\uB7B5\uBB3C\uC790\uC640 \uAD00\uB828\uB41C \uBC95\uB839\uC744 \uCC3E\uC544\uBCF4\uC138\uC694");
        assertThat(index).contains("\uD574\uC678 \uC5C5\uCCB4\uC5D0 \uAE30\uC220\uC790\uB8CC\uB97C \uC81C\uACF5\uD574\uB3C4 \uB418\uB098\uC694?");

        assertThat(admin).contains("<title>\uAD00\uB9AC - \uBC95\uB839 \uAC80\uC0C9 \uC5B4\uC2DC\uC2A4\uD134\uD2B8</title>");
        assertThat(admin).contains("\uAD00\uB9AC \uB300\uC2DC\uBCF4\uB4DC");
        assertThat(admin).contains("Provider \uC810\uAC80");
    }

    @Test
    void citedArticleContentSupportsAccessibleExpandAndCollapse() throws IOException {
        String app = read("app.js");
        String styles = read("styles.css");

        assertThat(app).contains("data-action=\"toggle-content\"");
        assertThat(app).contains("aria-expanded=\"false\"");
        assertThat(app).contains("\uC804\uCCB4 \uBCF4\uAE30");
        assertThat(app).contains("\uC811\uAE30");
        assertThat(styles).contains(".article-content.is-collapsed");
        assertThat(styles).contains(".article-content.is-expanded");
        assertThat(styles).contains(".content-toggle");
    }

    @Test
    void adminPageCanFilterAgentTracesByRequestId() throws IOException {
        String admin = read("admin.html");
        String adminJs = read("admin.js");
        String styles = read("styles.css");

        assertThat(admin).contains("id=\"trace-request-id\"");
        assertThat(admin).contains("id=\"clear-trace-filter\"");
        assertThat(adminJs).contains("data-trace-request-id");
        assertThat(adminJs).contains("/api/admin/agent-traces?requestId=");
        assertThat(adminJs).contains("loadAgentTraces(traceRequestIdInput.value)");
        assertThat(adminJs).contains("\uC0C9\uC778 \uC870\uBB38");
        assertThat(adminJs).contains("\uBBF8\uC0C9\uC778 \uC870\uBB38");
        assertThat(styles).contains(".trace-filter");
        assertThat(styles).contains(".trace-link");
    }

    @Test
    void questionPageUsesKoreanProcessLabelsWithoutPublicScoreCopy() throws IOException {
        String app = read("app.js");

        assertThat(app).contains("\uAC80\uC0C9 \uC870\uBB38");
        assertThat(app).contains("\uC778\uC6A9 \uC870\uBB38");
        assertThat(app).contains("\uD0A4\uC6CC\uB4DC \uD6C4\uBCF4");
        assertThat(app).contains("\uBCA1\uD130 \uD6C4\uBCF4");
        assertThat(app).contains("\uC9C8\uBB38 \uBD84\uC11D");
        assertThat(app).contains("\uB2F5\uBCC0 \uC791\uC131");
        assertThat(app).doesNotContain("\uC810\uC218 ${");
    }

    @Test
    void staticResourcesDoNotContainBrokenEncodingOrPortfolioCopy() throws IOException {
        List<Path> files;
        try (var stream = Files.list(STATIC_DIR)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".html") || path.toString().endsWith(".js"))
                    .toList();
        }

        assertThat(files).isNotEmpty();
        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(content)
                    .as(file.toString())
                    .doesNotContain(MOJIBAKE_MARKERS.toArray(String[]::new))
                    .doesNotContain(FORBIDDEN_COPY.toArray(String[]::new));
        }
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(STATIC_DIR.resolve(fileName), StandardCharsets.UTF_8);
    }
}
