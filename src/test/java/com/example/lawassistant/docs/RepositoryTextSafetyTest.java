package com.example.lawassistant.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepositoryTextSafetyTest {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".css", ".html", ".java", ".js", ".json", ".md", ".ps1", ".xml", ".yaml", ".yml"
    );
    private static final List<String> MOJIBAKE_MARKERS = List.of(
            "\u8E30", "\uF9DE", "\u6D39", "\u8B70", "\u5BC3", "\u5AC4", "\uCA0C", "\uFFFD"
    );

    @Test
    void repositoryTextFilesDoNotContainBrokenKoreanEncodingMarkers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(
                Path.of("README.md"),
                Path.of("handoff.md"),
                Path.of("AGENTS.md"),
                Path.of("PROJECT_GUIDE.md"),
                Path.of("docs"),
                Path.of("harness"),
                Path.of("scripts"),
                Path.of("src/main"),
                Path.of("src/test")
        )) {
            scan(root, violations);
        }

        assertThat(violations).isEmpty();
    }

    private static void scan(Path root, List<String> violations) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        if (Files.isRegularFile(root)) {
            inspect(root, violations);
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path file : stream
                    .filter(Files::isRegularFile)
                    .filter(RepositoryTextSafetyTest::isTextFile)
                    .toList()) {
                inspect(file, violations);
            }
        }
    }

    private static void inspect(Path file, List<String> violations) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        for (String marker : MOJIBAKE_MARKERS) {
            if (content.contains(marker)) {
                violations.add(file + " contains mojibake marker U+" + codePoint(marker));
            }
        }
    }

    private static boolean isTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static String codePoint(String marker) {
        return Integer.toHexString(marker.charAt(0)).toUpperCase();
    }
}
