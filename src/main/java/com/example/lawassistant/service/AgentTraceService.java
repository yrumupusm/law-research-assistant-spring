package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.AgentTrace;
import com.example.lawassistant.domain.enums.AgentStepStatus;
import com.example.lawassistant.repository.AgentTraceRepository;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceService {

    private final AgentTraceRepository repository;

    public AgentTraceService(AgentTraceRepository repository) {
        this.repository = repository;
    }

    public void record(
            String requestId,
            String stepName,
            String inputSummary,
            String outputSummary,
            AgentStepStatus status,
            long latencyMs,
            String errorMessage
    ) {
        repository.save(new AgentTrace(
                requestId,
                stepName,
                clip(inputSummary),
                clip(outputSummary),
                status,
                latencyMs,
                clip(errorMessage)
        ));
    }

    private String clip(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
