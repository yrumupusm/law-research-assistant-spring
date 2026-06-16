package com.example.lawassistant.api;

import com.example.lawassistant.dto.AdminStatusResponse;
import com.example.lawassistant.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api", "/api/v1"})
@Tag(name = "Health", description = "Basic service health check")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final AdminService adminService;

    public HealthController(JdbcTemplate jdbcTemplate, AdminService adminService) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminService = adminService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public Map<String, Object> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean dbOk = checkDatabase(checks);
        boolean indexOk = checkIndex(checks);
        return Map.of(
                "status", dbOk && indexOk ? "ok" : "degraded",
                "checks", checks
        );
    }

    private boolean checkDatabase(Map<String, Object> checks) {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            checks.put("db", "ok");
            return true;
        } catch (RuntimeException ex) {
            checks.put("db", "error:" + ex.getClass().getSimpleName());
            return false;
        }
    }

    private boolean checkIndex(Map<String, Object> checks) {
        try {
            AdminStatusResponse status = adminService.status();
            checks.put("index", Map.of(
                    "status", status.indexStatus(),
                    "articles", status.articlesCount(),
                    "indexedArticles", status.indexedArticlesCount(),
                    "unindexedArticles", status.unindexedArticlesCount()
            ));
            return "healthy".equals(status.indexStatus());
        } catch (RuntimeException ex) {
            checks.put("index", "error:" + ex.getClass().getSimpleName());
            return false;
        }
    }
}
