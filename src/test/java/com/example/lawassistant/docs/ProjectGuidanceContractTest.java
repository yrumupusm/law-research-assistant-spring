package com.example.lawassistant.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectGuidanceContractTest {

    @Test
    void agentInstructionsPreserveAuditAndScenarioRules() throws IOException {
        String agents = Files.readString(Path.of("AGENTS.md"), StandardCharsets.UTF_8);
        String guide = Files.readString(Path.of("PROJECT_GUIDE.md"), StandardCharsets.UTF_8);
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);

        assertThat(agents).contains("diagnostics.requestId");
        assertThat(agents).contains("SearchLog.requestId");
        assertThat(agents).contains("AgentTrace.requestId");
        assertThat(agents).contains("QueryAnalyzerAgent -> RetrievalAgent -> EvidenceValidatorAgent -> AnswerWriterAndCritic");
        assertThat(agents).contains("ScenarioScriptContractTest");

        assertThat(guide).contains("Runtime Audit And Scenario Rules");
        assertThat(guide).contains("scripts/run-scenarios.ps1");
        assertThat(guide).contains("GET /api/admin/agent-traces?requestId=...");
        assertThat(guide).contains("admin page requestId filter");

        assertThat(readme).contains("verify-readiness.ps1 -RequireExternalProviders");
        assertThat(readme).contains("verify-final.ps1");
        assertThat(readme).contains("서버를 시작, 중지, 재시작하지 않습니다");
        assertThat(readme).contains("target/evidence-report.md");
        assertThat(readme).contains("RequireQdrant");
        assertThat(readme).contains("RequireCohere");
    }
}
