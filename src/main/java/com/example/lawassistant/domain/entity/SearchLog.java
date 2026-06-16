package com.example.lawassistant.domain.entity;

import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_logs")
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String requestId;

    @Column(nullable = false, length = 64)
    private String questionHash;

    @Column(length = 160)
    private String questionPreview;

    @Column(nullable = false)
    private Integer questionLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SearchStatus status;

    private Double confidence;
    private String snapshotVersion;
    private LocalDate asOf;
    private Integer citedArticleCount;
    private Integer latencyAnalyzeMs;
    private Integer latencyRetrieveMs;
    private Integer latencySynthesizeMs;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected SearchLog() {
    }

    public SearchLog(
            String requestId,
            String questionHash,
            String questionPreview,
            Integer questionLength,
            QuestionType questionType,
            SearchStatus status,
            Double confidence,
            String snapshotVersion,
            LocalDate asOf,
            Integer citedArticleCount,
            Integer latencyAnalyzeMs,
            Integer latencyRetrieveMs,
            Integer latencySynthesizeMs
    ) {
        this.requestId = requestId;
        this.questionHash = questionHash;
        this.questionPreview = questionPreview;
        this.questionLength = questionLength;
        this.questionType = questionType;
        this.status = status;
        this.confidence = confidence;
        this.snapshotVersion = snapshotVersion;
        this.asOf = asOf;
        this.citedArticleCount = citedArticleCount;
        this.latencyAnalyzeMs = latencyAnalyzeMs;
        this.latencyRetrieveMs = latencyRetrieveMs;
        this.latencySynthesizeMs = latencySynthesizeMs;
    }

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getQuestionHash() {
        return questionHash;
    }

    public String getQuestionPreview() {
        return questionPreview;
    }

    public Integer getQuestionLength() {
        return questionLength;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public SearchStatus getStatus() {
        return status;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getSnapshotVersion() {
        return snapshotVersion;
    }

    public LocalDate getAsOf() {
        return asOf;
    }

    public Integer getCitedArticleCount() {
        return citedArticleCount;
    }

    public Integer getLatencyAnalyzeMs() {
        return latencyAnalyzeMs;
    }

    public Integer getLatencyRetrieveMs() {
        return latencyRetrieveMs;
    }

    public Integer getLatencySynthesizeMs() {
        return latencySynthesizeMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
