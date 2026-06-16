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
            "demo", "sample", "mini project", "포트폴리오", "미니 프로젝트", "데모", "샘플",
            "실제 회사 내부 데이터", "This response", "sample articles", "demo project",
            "신뢰도", "confidence", "검증됨"
    );

    @Test
    void staticPagesKeepReadableKoreanCopy() throws IOException {
        String index = read("index.html");
        String admin = read("admin.html");

        assertThat(index).contains("<title>법령 리서치 어시스턴트</title>");
        assertThat(index).contains("질문에 맞는 법령 근거를 찾아 정리합니다");
        assertThat(index).contains("해외 업체에 기술자료를 제공해도 되나요?");

        assertThat(admin).contains("<title>관리 - 법령 리서치 어시스턴트</title>");
        assertThat(admin).contains("관리 대시보드");
        assertThat(admin).contains("Provider 점검");
    }

    @Test
    void citedArticleContentSupportsAccessibleExpandAndCollapse() throws IOException {
        String app = read("app.js");
        String styles = read("styles.css");

        assertThat(app).contains("data-action=\"toggle-content\"");
        assertThat(app).contains("aria-expanded=\"false\"");
        assertThat(app).contains("전체 보기");
        assertThat(app).contains("접기");
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
        assertThat(adminJs).contains("색인 조문");
        assertThat(adminJs).contains("미색인 조문");
        assertThat(styles).contains(".trace-filter");
        assertThat(styles).contains(".trace-link");
    }

    @Test
    void questionPageUsesKoreanProcessLabelsWithoutPublicScoreCopy() throws IOException {
        String app = read("app.js");

        assertThat(app).contains("검색 조문");
        assertThat(app).contains("인용 조문");
        assertThat(app).contains("키워드 후보");
        assertThat(app).contains("벡터 후보");
        assertThat(app).contains("질문 분석");
        assertThat(app).contains("답변 작성");
        assertThat(app).doesNotContain("점수 ${");
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
