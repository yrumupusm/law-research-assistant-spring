package com.example.lawassistant.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OriginalParityContractTest {

    @Test
    void originalParityDocumentMapsP0ScopeToSpringArtifacts() throws IOException {
        String parity = read("docs/original-parity.md");

        assertThat(parity).contains("portfolio-scale reimplementation");
        assertThat(parity).contains("Original `CLAUDE.md`");
        assertThat(parity).contains("Original `docs/p0-scope.md`");
        assertThat(parity).contains("Original `docs/architecture.md`");
        assertThat(parity).contains("Original `docs/constraints.md`");

        assertThat(parity).contains("LocalLawIngestionService");
        assertThat(parity).contains("MarkdownLawParser");
        assertThat(parity).contains("VectorIndexService");
        assertThat(parity).contains("QueryAnalyzerAgent");
        assertThat(parity).contains("RetrievalAgent");
        assertThat(parity).contains("EvidenceValidatorAgent");
        assertThat(parity).contains("AnswerWriterAgent");
        assertThat(parity).contains("CriticAgent");
        assertThat(parity).contains("SearchLogAgent");
    }

    @Test
    void originalParityDocumentPreservesResponseAndProviderContracts() throws IOException {
        String parity = read("docs/original-parity.md");

        assertThat(parity).contains("status=OK requires citations");
        assertThat(parity).contains("No legal final determination");
        assertThat(parity).contains("snapshotVersion");
        assertThat(parity).contains("indexedAt");
        assertThat(parity).contains("sourcePath");
        assertThat(parity).contains("requestId");

        assertThat(parity).contains("ChatModelClient");
        assertThat(parity).contains("EmbeddingClient");
        assertThat(parity).contains("VectorSearchClient");
        assertThat(parity).contains("RerankerClient");

        assertThat(parity).contains("/api/v1/ask");
        assertThat(parity).contains("candidate_laws");
        assertThat(parity).contains("cited_articles");
        assertThat(parity).contains("follow_up_questions");
        assertThat(parity).contains("effective_basis");
        assertThat(parity).contains("retrieval_stats");
    }

    @Test
    void originalParityDocumentIncludesRuntimeProofGate() throws IOException {
        String parity = read("docs/original-parity.md");

        assertThat(parity).contains("scripts/verify-local.ps1");
        assertThat(parity).contains("scripts/verify-readiness.ps1");
        assertThat(parity).contains("scripts/verify-runtime.ps1");
        assertThat(parity).contains("scripts/run-scenarios.ps1");
        assertThat(parity).contains("scripts/export-evidence-report.ps1");
        assertThat(parity).contains("scripts/verify-completion-evidence.ps1");
        assertThat(parity).contains("scripts/verify-final.ps1");
        assertThat(parity).contains("does not start, stop, or restart");
    }

    @Test
    void originalParityDocumentDoesNotClaimLineByLinePortOrUseSensitiveData() throws IOException {
        String parity = read("docs/original-parity.md");

        assertThat(parity).contains("No company-confidential code");
        assertThat(parity).doesNotContain("is a line-by-line port");
        assertThat(parity).doesNotContain("API key:");
        assertThat(parity).doesNotContain("OPENROUTER_API_KEY=");
        assertThat(parity).doesNotContain("COHERE_API_KEY=");
        assertThat(parity).doesNotContain("QDRANT_API_KEY=");
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file), StandardCharsets.UTF_8);
    }
}
