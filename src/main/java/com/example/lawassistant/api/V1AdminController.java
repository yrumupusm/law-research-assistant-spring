package com.example.lawassistant.api;

import com.example.lawassistant.dto.IngestLocalRequest;
import com.example.lawassistant.dto.SyncSourceRequest;
import com.example.lawassistant.service.AdminService;
import com.example.lawassistant.service.ProviderSmokeTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin v1 Compatibility", description = "Original law_research_assistant compatible admin APIs")
public class V1AdminController {

    private final AdminService adminService;
    private final ProviderSmokeTestService providerSmokeTestService;
    private final V1ResponseMapper v1ResponseMapper;

    public V1AdminController(
            AdminService adminService,
            ProviderSmokeTestService providerSmokeTestService,
            V1ResponseMapper v1ResponseMapper
    ) {
        this.adminService = adminService;
        this.providerSmokeTestService = providerSmokeTestService;
        this.v1ResponseMapper = v1ResponseMapper;
    }

    @GetMapping("/status")
    @Operation(summary = "Get index and corpus status with original v1 response field names")
    public Map<String, Object> status() {
        return v1ResponseMapper.toSnakeCaseMap(adminService.status());
    }

    @GetMapping("/search-logs")
    @Operation(summary = "List search audit logs with original v1 response field names")
    public Map<String, Object> searchLogs() {
        return v1ResponseMapper.toSnakeCaseMap(adminService.searchLogs());
    }

    @GetMapping("/agent-traces")
    @Operation(summary = "List agent execution traces with original v1 response field names")
    public Map<String, Object> agentTraces(@RequestParam(required = false) String requestId) {
        return v1ResponseMapper.toSnakeCaseMap(adminService.agentTraces(requestId));
    }

    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Rebuild vector index with original v1 response field names")
    public Map<String, Object> reindex() {
        return v1ResponseMapper.toSnakeCaseMap(adminService.scheduleReindex());
    }

    @PostMapping("/ingest-local")
    @Operation(summary = "Ingest local markdown laws with original v1 response field names")
    public Map<String, Object> ingestLocal(@RequestBody(required = false) IngestLocalRequest request) {
        return v1ResponseMapper.toSnakeCaseMap(adminService.ingestLocal(request));
    }

    @PostMapping("/sync-source")
    @Operation(summary = "Sync source repository with original v1 response field names")
    public Map<String, Object> syncSource(@RequestBody(required = false) SyncSourceRequest request) {
        return v1ResponseMapper.toSnakeCaseMap(adminService.syncSource(request));
    }

    @GetMapping("/ingestion-runs")
    @Operation(summary = "List ingestion and reindex runs with original v1 response field names")
    public Map<String, Object> ingestionRuns() {
        return v1ResponseMapper.toSnakeCaseMap(adminService.ingestionRuns());
    }

    @PostMapping("/provider-smoke-test")
    @Operation(summary = "Run provider smoke test with original v1 response field names")
    public Map<String, Object> providerSmokeTest() {
        return v1ResponseMapper.toSnakeCaseMap(providerSmokeTestService.run());
    }
}
