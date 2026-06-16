package com.example.lawassistant.api;

import com.example.lawassistant.dto.AskRequest;
import com.example.lawassistant.service.AskOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ask v1 Compatibility", description = "Original law_research_assistant compatible ask API")
public class V1AskController {

    private final AskOrchestratorService askOrchestratorService;
    private final V1ResponseMapper v1ResponseMapper;

    public V1AskController(AskOrchestratorService askOrchestratorService, V1ResponseMapper v1ResponseMapper) {
        this.askOrchestratorService = askOrchestratorService;
        this.v1ResponseMapper = v1ResponseMapper;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a legal research question with original v1 response field names")
    public Map<String, Object> ask(@Valid @RequestBody AskRequest request) {
        return v1ResponseMapper.toSnakeCaseMap(askOrchestratorService.ask(request.question(), request.asOf()));
    }
}
