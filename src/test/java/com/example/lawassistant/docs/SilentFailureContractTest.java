package com.example.lawassistant.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SilentFailureContractTest {

    private static final Pattern IGNORED_CATCH = Pattern.compile("catch \\(([^)]+) ignored\\)");
    private static final Pattern GENERIC_CATCH = Pattern.compile("catch \\((Exception|RuntimeException)\\s+\\w+\\)");

    @Test
    void mainCodeDoesNotSilentlyIgnoreGenericExceptions() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainJavaFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            var matcher = GENERIC_CATCH.matcher(content);
            while (matcher.find()) {
                String line = lineContaining(content, matcher.start());
                if (!line.contains("catch (RuntimeException ex)")
                        && !line.contains("catch (RuntimeException batchFailure)")
                        && !line.contains("catch (RuntimeException singleFailure)")) {
                    violations.add(file + ": " + line.trim());
                }
            }
        }

        assertThat(violations)
                .as("Generic exception catches must either propagate or produce explicit FAILED audit paths")
                .isEmpty();
    }

    @Test
    void ignoredExceptionsStayNarrowAndDocumented() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : mainJavaFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            var matcher = IGNORED_CATCH.matcher(content);
            while (matcher.find()) {
                String catchType = matcher.group(1);
                String location = file.toString().replace('\\', '/');
                boolean allowed = location.endsWith("QdrantVectorSearchClient.java")
                        && (catchType.equals("RestClientException") || catchType.equals("NumberFormatException"));
                if (!allowed) {
                    violations.add(file + ": " + lineContaining(content, matcher.start()).trim());
                }
            }
        }

        assertThat(violations)
                .as("Ignored exceptions are allowed only for Qdrant collection existence probing and point id parsing")
                .isEmpty();
    }

    @Test
    void qdrantSearchSchemaMismatchIsNotReportedAsEmptySearchResult() throws IOException {
        String content = Files.readString(
                Path.of("src/main/java/com/example/lawassistant/infrastructure/vector/QdrantVectorSearchClient.java"),
                StandardCharsets.UTF_8
        );

        assertThat(content).contains("Qdrant search response did not contain a result array.");
        assertThat(content).doesNotContain("if (!result.isArray()) {\n            return List.of();");
    }

    @Test
    void guidancePreservesSilentFailureRules() throws IOException {
        String agents = Files.readString(Path.of("AGENTS.md"), StandardCharsets.UTF_8);

        assertThat(agents).contains("Do not return `status=OK` with an empty `citedArticles` list.");
        assertThat(agents).contains("Return safe failure codes in `AskResponse.errorMessage`");
        assertThat(agents).contains("Do not hide provider, retrieval, parsing, or validation failures as empty results.");
    }

    private static List<Path> mainJavaFiles() throws IOException {
        try (var stream = Files.walk(Path.of("src/main/java"))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static String lineContaining(String content, int index) {
        int start = content.lastIndexOf('\n', index);
        int end = content.indexOf('\n', index);
        if (start < 0) {
            start = 0;
        } else {
            start += 1;
        }
        if (end < 0) {
            end = content.length();
        }
        return content.substring(start, end);
    }
}
