package com.example.lawassistant.domain.entity;

import com.example.lawassistant.domain.enums.IngestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingestion_runs")
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngestionStatus status = IngestionStatus.RUNNING;

    @Column(length = 2000)
    private String errorMessage;

    private Integer filesProcessed = 0;
    private Integer filesFailed = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_version_id")
    private SnapshotVersion snapshotVersion;

    protected IngestionRun() {
    }

    public static IngestionRun start(SnapshotVersion snapshotVersion) {
        IngestionRun run = new IngestionRun();
        run.snapshotVersion = snapshotVersion;
        run.status = IngestionStatus.RUNNING;
        run.startedAt = LocalDateTime.now();
        return run;
    }

    public void succeed(int filesProcessed) {
        complete(filesProcessed, 0, null);
    }

    public void succeed(int filesProcessed, int filesFailed) {
        complete(filesProcessed, filesFailed, null);
    }

    public void complete(int filesProcessed, int filesFailed, String errorMessage) {
        this.status = filesFailed > 0 ? IngestionStatus.PARTIAL_FAILED : IngestionStatus.SUCCEEDED;
        this.filesProcessed = filesProcessed;
        this.filesFailed = filesFailed;
        this.errorMessage = errorMessage;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.status = IngestionStatus.FAILED;
        this.errorMessage = errorMessage;
        this.filesFailed = 1;
        this.finishedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getFilesProcessed() {
        return filesProcessed;
    }

    public Integer getFilesFailed() {
        return filesFailed;
    }

    public SnapshotVersion getSnapshotVersion() {
        return snapshotVersion;
    }
}
