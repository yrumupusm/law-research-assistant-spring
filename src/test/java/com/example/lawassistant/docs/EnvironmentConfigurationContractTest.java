package com.example.lawassistant.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class EnvironmentConfigurationContractTest {

    private static final Pattern ENV_LINE = Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)\\s*=.*$");
    private static final Pattern SPRING_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)(?::|})");
    private static final Pattern POWERSHELL_ENV_CHECK = Pattern.compile(
            "(?:Get-EnvValue|Test-HasValue|Require-[A-Za-z]+|Require-AllowedValue)\\s+\"([A-Z][A-Z0-9_]*)\""
    );

    @Test
    void envExampleDocumentsEveryRuntimePlaceholder() throws IOException {
        Set<String> exampleNames = envExampleNames();
        Set<String> runtimeNames = new TreeSet<>();
        runtimeNames.addAll(matches(Path.of("src/main/resources/application.yml"), SPRING_PLACEHOLDER));
        runtimeNames.addAll(matches(Path.of("docker-compose.yml"), SPRING_PLACEHOLDER));

        assertThat(exampleNames)
                .as(".env.example must document all Spring and Docker env placeholders")
                .containsAll(runtimeNames);
    }

    @Test
    void envExampleDocumentsEveryPreflightCheck() throws IOException {
        Set<String> exampleNames = envExampleNames();
        Set<String> preflightNames = matches(Path.of("scripts/preflight.ps1"), POWERSHELL_ENV_CHECK);

        assertThat(exampleNames)
                .as(".env.example must document every env var checked by preflight")
                .containsAll(preflightNames);
    }

    @Test
    void exampleKeepsProviderAndSafetyDefaultsExplicit() throws IOException {
        Set<String> names = envExampleNames();

        assertThat(names).contains(
                "LLM_PROVIDER",
                "EMBEDDING_PROVIDER",
                "RERANKER_PROVIDER",
                "VECTOR_PROVIDER",
                "OPENROUTER_API_KEY",
                "LLM_API_KEY",
                "EMBEDDING_API_KEY",
                "SERVER_PORT",
                "QDRANT_PORT",
                "LOG_SENSITIVE_DATA",
                "STORE_RAW_QUESTION",
                "ENABLE_AGENT_TRACE",
                "ADMIN_REINDEX_ENABLED"
        );
    }

    private static Set<String> envExampleNames() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String line : Files.readAllLines(Path.of(".env.example"), StandardCharsets.UTF_8)) {
            var matcher = ENV_LINE.matcher(line);
            if (matcher.matches()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static Set<String> matches(Path path, Pattern pattern) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Set<String> names = new TreeSet<>();
        var matcher = pattern.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
