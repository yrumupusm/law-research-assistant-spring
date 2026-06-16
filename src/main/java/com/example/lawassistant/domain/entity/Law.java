package com.example.lawassistant.domain.entity;

import com.example.lawassistant.domain.enums.LawType;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "laws",
        uniqueConstraints = @UniqueConstraint(name = "uq_law_slug_snapshot", columnNames = {"slug", "snapshot_version_id"})
)
public class Law {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String slug;

    @Column(nullable = false, length = 512)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LawType lawType;

    @Column(length = 80)
    private String lawNumber;

    private LocalDate enactedAt;
    private LocalDate lastAmendedAt;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @Column(length = 500)
    private String sourcePath;

    @Column(length = 4000)
    private String metadataJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_version_id", nullable = false)
    private SnapshotVersion snapshotVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Law() {
    }

    public Law(String slug, String title, LawType lawType, String lawNumber, SnapshotVersion snapshotVersion) {
        this.slug = slug;
        this.title = title;
        this.lawType = lawType;
        this.lawNumber = lawNumber;
        this.snapshotVersion = snapshotVersion;
    }

    public void applyMetadata(
            LocalDate enactedAt,
            LocalDate lastAmendedAt,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String sourcePath,
            String metadataJson
    ) {
        this.enactedAt = enactedAt;
        this.lastAmendedAt = lastAmendedAt;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.sourcePath = sourcePath;
        this.metadataJson = metadataJson;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public LawType getLawType() {
        return lawType;
    }

    public String getLawNumber() {
        return lawNumber;
    }

    public LocalDate getEnactedAt() {
        return enactedAt;
    }

    public LocalDate getLastAmendedAt() {
        return lastAmendedAt;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public SnapshotVersion getSnapshotVersion() {
        return snapshotVersion;
    }

    public String getSourcePath() {
        return sourcePath;
    }
}
