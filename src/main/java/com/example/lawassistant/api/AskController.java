package com.example.lawassistant.api;

import com.example.lawassistant.dto.AskRequest;
import com.example.lawassistant.dto.AskResponse;
import com.example.lawassistant.service.AskOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Ask", description = "RAG/Agent based legal research question answering")
public class AskController {

    private final AskOrchestratorService askOrchestratorService;

    public AskController(AskOrchestratorService askOrchestratorService) {
        this.askOrchestratorService = askOrchestratorService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a legal research question", description = "Analyzes the question, retrieves related law articles, writes a cited answer, and records diagnostics.")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return askOrchestratorService.ask(request.question(), request.asOf());
    }
}
