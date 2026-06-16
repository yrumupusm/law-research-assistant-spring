package com.example.lawassistant.dto;

import com.example.lawassistant.domain.enums.AgentStepStatus;
import java.time.LocalDateTime;

public record AgentTraceItemDto(
        Long id,
        String requestId,
        String stepName,
        String inputSummary,
        String outputSummary,
        AgentStepStatus status,
        Long latencyMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
