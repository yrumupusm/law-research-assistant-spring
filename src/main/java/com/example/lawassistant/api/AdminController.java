package com.example.lawassistant.api;

import com.example.lawassistant.dto.AdminStatusResponse;
import com.example.lawassistant.dto.AgentTraceListResponse;
import com.example.lawassistant.dto.IngestLocalRequest;
import com.example.lawassistant.dto.IngestLocalResponse;
import com.example.lawassistant.dto.IngestionRunListResponse;
import com.example.lawassistant.dto.ProviderSmokeTestResponse;
import com.example.lawassistant.dto.ReindexResponse;
import com.example.lawassistant.dto.SearchLogListResponse;
import com.example.lawassistant.dto.SyncSourceRequest;
import com.example.lawassistant.dto.SyncSourceResponse;
import com.example.lawassistant.service.AdminService;
import com.example.lawassistant.service.ProviderSmokeTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Index status, audit logs, agent traces, and provider smoke tests")
public class AdminController {

    private final AdminService adminService;
    private final ProviderSmokeTestService providerSmokeTestService;

    public AdminController(
            AdminService adminService,
            ProviderSmokeTestService providerSmokeTestService
    ) {
        this.adminService = adminService;
        this.providerSmokeTestService = providerSmokeTestService;
    }

    @GetMapping("/status")
    @Operation(summary = "Get index and corpus status")
    public AdminStatusResponse status() {
        return adminService.status();
    }

    @GetMapping("/search-logs")
    @Operation(summary = "List search audit logs")
    public SearchLogListResponse searchLogs() {
        return adminService.searchLogs();
    }

    @GetMapping("/agent-traces")
    @Operation(summary = "List agent execution traces")
    public AgentTraceListResponse agentTraces(@RequestParam(required = false) String requestId) {
        return adminService.agentTraces(requestId);
    }

    @PostMapping("/reindex")
    @Operation(summary = "Rebuild vector index", description = "Embeds current articles and records a reindex run.")
    public ReindexResponse reindex() {
        return adminService.reindex();
    }

    @PostMapping("/ingest-local")
    @Operation(summary = "Ingest local markdown laws", description = "Parses local Markdown law files, stores a new snapshot, and rebuilds the index.")
    public IngestLocalResponse ingestLocal(@RequestBody(required = false) IngestLocalRequest request) {
        return adminService.ingestLocal(request);
    }

    @PostMapping("/sync-source")
    @Operation(summary = "Sync source repository", description = "Clones or pulls the configured law source repository and can ingest it immediately.")
    public SyncSourceResponse syncSource(@RequestBody(required = false) SyncSourceRequest request) {
        return adminService.syncSource(request);
    }

    @GetMapping("/ingestion-runs")
    @Operation(summary = "List ingestion and reindex runs")
    public IngestionRunListResponse ingestionRuns() {
        return adminService.ingestionRuns();
    }

    @PostMapping("/provider-smoke-test")
    @Operation(summary = "Run provider smoke test", description = "Calls the configured chat model, embedding provider, and reranker provider once.")
    public ProviderSmokeTestResponse providerSmokeTest() {
        return providerSmokeTestService.run();
    }
}
