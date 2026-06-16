package com.example.lawassistant.domain.entity;

import com.example.lawassistant.domain.enums.AgentStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_traces")
public class AgentTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String requestId;

    @Column(nullable = false, length = 80)
    private String stepName;

    @Column(length = 500)
    private String inputSummary;

    @Column(length = 500)
    private String outputSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentStepStatus status;

    private Long latencyMs;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AgentTrace() {
    }

    public AgentTrace(
            String requestId,
            String stepName,
            String inputSummary,
            String outputSummary,
            AgentStepStatus status,
            Long latencyMs,
            String errorMessage
    ) {
        this.requestId = requestId;
        this.stepName = stepName;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.status = status;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getStepName() {
        return stepName;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public AgentStepStatus getStatus() {
        return status;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
