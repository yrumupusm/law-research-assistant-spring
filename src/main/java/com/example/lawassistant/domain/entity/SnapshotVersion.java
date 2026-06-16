package com.example.lawassistant.domain.entity;

import com.example.lawassistant.domain.enums.SnapshotStatus;
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
@Table(name = "snapshot_versions")
public class SnapshotVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SnapshotStatus status = SnapshotStatus.CREATED;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime indexedAt;

    @Column(length = 120)
    private String sourceVersion;

    @Column(length = 500)
    private String sourcePath;

    @Column(length = 2000)
    private String notes;

    protected SnapshotVersion() {
    }

    public SnapshotVersion(String version, SnapshotStatus status, LocalDateTime indexedAt, String sourcePath) {
        this.version = version;
        this.status = status;
        this.indexedAt = indexedAt;
        this.sourcePath = sourcePath;
    }

    public Long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public SnapshotStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public String getSourcePath() {
        return sourcePath;
    }
}
