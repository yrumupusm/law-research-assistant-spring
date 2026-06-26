package com.example.lawassistant.scripts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScenarioScriptContractTest {

    @Test
    void scenarioRunnerChecksRequestAuditTrail() throws IOException {
        String script = Files.readString(Path.of("scripts/run-scenarios.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("diagnostics.requestId");
        assertThat(script).contains("/api/admin/search-logs");
        assertThat(script).contains("/api/admin/agent-traces?requestId=");
        assertThat(script).contains("QueryAnalyzerAgent");
        assertThat(script).contains("RetrievalAgent");
        assertThat(script).contains("EvidenceValidatorAgent");
        assertThat(script).contains("AnswerWriterAndCritic");
        assertThat(script).contains("agent trace sequence invalid");
        assertThat(script).contains("scenario-summary.json");
        assertThat(script).contains("summaryItems");
        assertThat(script).contains("searchLogOk");
        assertThat(script).contains("traceOk");
        assertThat(script).contains("forbiddenCopy");
        assertThat(script).contains("Scenario summary:");
        assertThat(script).contains("EnvFile");
        assertThat(script).contains("SERVER_PORT");
        assertThat(script).contains("Get-EnvFileValue");
    }

    @Test
    void runtimeVerifierChecksHealthAndOriginalCompatibleV1Contracts() throws IOException {
        String script = Files.readString(Path.of("scripts/verify-runtime.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("/healthz");
        assertThat(script).contains("/api/v1/health");
        assertThat(script).contains("/api/v1/ask");
        assertThat(script).contains("/api/v1/admin/status");
        assertThat(script).contains("cited_articles");
        assertThat(script).contains("effective_basis");
        assertThat(script).contains("request_id");
        assertThat(script).contains("reindex_enabled");
        assertThat(script).contains("camelCase");
        assertThat(script).contains("IncludeReindexContract");
        assertThat(script).contains("/api/v1/admin/reindex");
        assertThat(script).contains("ingestion_run_id");
        assertThat(script).contains("V1Compatibility=checked");
        assertThat(script).contains("runtime-evidence");
        assertThat(script).contains("runtime-summary.json");
        assertThat(script).contains("admin-status.json");
        assertThat(script).contains("v1-admin-status.json");
        assertThat(script).contains("v1-ask-response.json");
        assertThat(script).contains("EvidenceDir=");
        assertThat(script).contains("EnvFile");
        assertThat(script).contains("SERVER_PORT");
        assertThat(script).contains("Get-EnvFileValue");
    }

    @Test
    void preflightChecksProviderEnvironmentWithoutPrintingSecrets() throws IOException {
        String script = Files.readString(Path.of("scripts/preflight.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("OPENROUTER_API_KEY");
        assertThat(script).contains("LLM_PROVIDER");
        assertThat(script).contains("EMBEDDING_PROVIDER");
        assertThat(script).contains("RERANKER_PROVIDER");
        assertThat(script).contains("VECTOR_PROVIDER");
        assertThat(script).contains("QDRANT_BASE_URL");
        assertThat(script).contains("SERVER_PORT");
        assertThat(script).contains("LLM_TEMPERATURE");
        assertThat(script).contains("RERANKER_TOP_K");
        assertThat(script).contains("POSTGRES_PORT");
        assertThat(script).contains("QDRANT_PORT");
        assertThat(script).contains("LOG_SENSITIVE_DATA=true is not allowed");
        assertThat(script).contains("STORE_RAW_QUESTION=true is not allowed");
        assertThat(script).contains("Secrets: values are checked for presence only and are not printed.");
        assertThat(script).contains("verify-runtime.ps1");
        assertThat(script).contains("run-scenarios.ps1");
    }

    @Test
    void localVerifierRunsServerIndependentQualityGates() throws IOException {
        String script = Files.readString(Path.of("scripts/verify-local.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("preflight.ps1");
        assertThat(script).contains("Test-PowerShellScript");
        assertThat(script).contains("export-evidence-report.ps1");
        assertThat(script).contains("verify-completion-evidence.ps1");
        assertThat(script).contains("verify-final.ps1");
        assertThat(script).contains("verify-readiness.ps1");
        assertThat(script).contains("verify-runtime.ps1");
        assertThat(script).contains("run-scenarios.ps1");
        assertThat(script).contains("server.ps1");
        assertThat(script).contains("infra.ps1");
        assertThat(script).contains("node --check");
        assertThat(script).contains("app.js");
        assertThat(script).contains("admin.js");
        assertThat(script).contains("maven.compiler.useIncrementalCompilation=false");
        assertThat(script).contains("\"-q\" \"-Dmaven.compiler.useIncrementalCompilation=false\" \"test\"");
        assertThat(script).contains("Server/runtime checks still require a running application");
    }

    @Test
    void readinessVerifierSummarizesExternalProviderReadinessWithoutStartingServer() throws IOException {
        String script = Files.readString(Path.of("scripts/verify-readiness.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("RequireExternalProviders");
        assertThat(script).contains("@(\"LLM_PROVIDER\", \"EMBEDDING_PROVIDER\")");
        assertThat(script).contains("providerMustMatch");
        assertThat(script).contains("RequireQdrant");
        assertThat(script).contains("RequireCohere");
        assertThat(script).contains("preflight.ps1");
        assertThat(script).contains("OPENROUTER_API_KEY");
        assertThat(script).contains("LLM_API_KEY");
        assertThat(script).contains("EMBEDDING_API_KEY");
        assertThat(script).contains("RERANKER_API_KEY");
        assertThat(script).contains("QDRANT_BASE_URL");
        assertThat(script).contains("readiness-summary.json");
        assertThat(script).contains("docs\\original-parity.md");
        assertThat(script).contains("docs\\runtime-readiness.md");
        assertThat(script).contains("scripts\\verify-completion-evidence.ps1");
        assertThat(script).contains("scripts\\verify-final.ps1");
        assertThat(script).contains("verify-final.ps1");
        assertThat(script).contains("verify-runtime.ps1 -IncludeProviderSmoke");
        assertThat(script).contains("run-scenarios.ps1");
        assertThat(script).contains("export-evidence-report.ps1");
        assertThat(script).contains("verify-completion-evidence.ps1");
        assertThat(script).contains("Secrets: values were checked for presence only and were not printed.");
        assertThat(script).doesNotContain("server.ps1 start");
        assertThat(script).doesNotContain("server.ps1 restart");
        assertThat(script).doesNotContain("taskkill");
    }

    @Test
    void serverScriptUsesEnvFilePortForStatusUrls() throws IOException {
        String script = Files.readString(Path.of("scripts/server.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains(".env");
        assertThat(script).contains("SERVER_PORT");
        assertThat(script).contains("Get-EnvFileValue");
        assertThat(script).contains("$BaseUrl = \"http://localhost:$ServerPort\"");
        assertThat(script).contains("$StatusUrl = \"$BaseUrl/api/admin/status\"");
        assertThat(script).contains("$HealthzUrl = \"$BaseUrl/healthz\"");
        assertThat(script).contains("$V1HealthUrl = \"$BaseUrl/api/v1/health\"");
        assertThat(script).contains("[string]$MavenPath");
        assertThat(script).contains("Resolve-MavenCommand");
        assertThat(script).contains("Get-Command \"mvn.cmd\"");
        assertThat(script).contains("Set -MavenPath, set MAVEN_CMD, or install mvn.cmd on PATH");
        assertThat(script).contains("healthzHttp=");
        assertThat(script).contains("v1HealthHttp=");
    }

    @Test
    void evidenceReportExporterBuildsSafeMarkdownFromRuntimeAndScenarioSummaries() throws IOException {
        String script = Files.readString(Path.of("scripts/export-evidence-report.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("readiness-summary.json");
        assertThat(script).contains("Configuration Readiness");
        assertThat(script).contains("Readiness Warnings");
        assertThat(script).contains("runtime-summary.json");
        assertThat(script).contains("scenario-summary.json");
        assertThat(script).contains("evidence-report.md");
        assertThat(script).contains("Law Research Assistant Runtime Evidence");
        assertThat(script).contains("Runtime Smoke");
        assertThat(script).contains("Scenario Details");
        assertThat(script).contains("Safety Notes");
        assertThat(script).contains("readiness, runtime, and scenario evidence JSON files");
        assertThat(script).contains("It does not include API keys or full user question text.");
        assertThat(script).contains("Request IDs are retained");
        assertThat(script).doesNotContain("OPENROUTER_API_KEY");
        assertThat(script).doesNotContain("RERANKER_API_KEY");
    }

    @Test
    void completionEvidenceVerifierFailsWhenFinalRuntimeEvidenceIsIncomplete() throws IOException {
        String script = Files.readString(Path.of("scripts/verify-completion-evidence.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("readiness-summary.json");
        assertThat(script).contains("runtime-summary.json");
        assertThat(script).contains("scenario-summary.json");
        assertThat(script).contains("evidence-report.md");
        assertThat(script).contains("failedCount");
        assertThat(script).contains("RequireExternalProviders");
        assertThat(script).contains("providerSmokeIncluded");
        assertThat(script).contains("askCitationCount");
        assertThat(script).contains("v1AskCitationCount");
        assertThat(script).contains("askRequestId");
        assertThat(script).contains("v1AskRequestId");
        assertThat(script).contains("searchLogOk");
        assertThat(script).contains("traceOk");
        assertThat(script).contains("forbiddenCopy");
        assertThat(script).contains("Completion evidence passed.");
        assertThat(script).contains("OPENROUTER_API_KEY");
        assertThat(script).contains("RERANKER_API_KEY");
        assertThat(script).contains("EMBEDDING_API_KEY");
        assertThat(script).contains("LLM_API_KEY");
        assertThat(script).doesNotContain("server.ps1 start");
        assertThat(script).doesNotContain("server.ps1 restart");
        assertThat(script).doesNotContain("taskkill");
    }

    @Test
    void finalVerifierRunsRuntimeEvidenceFlowWithoutManagingServer() throws IOException {
        String script = Files.readString(Path.of("scripts/verify-final.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains("verify-readiness.ps1");
        assertThat(script).contains("-RequireExternalProviders");
        assertThat(script).contains("verify-runtime.ps1");
        assertThat(script).contains("-IncludeProviderSmoke");
        assertThat(script).contains("run-scenarios.ps1");
        assertThat(script).contains("export-evidence-report.ps1");
        assertThat(script).contains("verify-completion-evidence.ps1");
        assertThat(script).contains("Assert-LastExitCode");
        assertThat(script).contains("$LASTEXITCODE");
        assertThat(script).contains("Final runtime verification passed.");
        assertThat(script).contains("Evidence report:");
        assertThat(script).contains("RequireQdrant");
        assertThat(script).contains("RequireCohere");
        assertThat(script).contains("IncludeReindexContract");
        assertThat(script).doesNotContain("server.ps1 start");
        assertThat(script).doesNotContain("server.ps1 restart");
        assertThat(script).doesNotContain("taskkill");
    }
}
