package com.example.lawassistant.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_states")
public class SyncState {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(length = 80)
    private String lastSyncedCommitSha;

    private LocalDateTime lastSyncAt;

    private LocalDateTime lastForcePushDetectedAt;

    protected SyncState() {
    }

    public static SyncState empty() {
        return new SyncState();
    }

    public void markSynced(String commitSha) {
        this.lastSyncedCommitSha = commitSha;
        this.lastSyncAt = LocalDateTime.now();
    }

    public void markForcePushDetected() {
        this.lastForcePushDetectedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getLastSyncedCommitSha() {
        return lastSyncedCommitSha;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public LocalDateTime getLastForcePushDetectedAt() {
        return lastForcePushDetectedAt;
    }
}
