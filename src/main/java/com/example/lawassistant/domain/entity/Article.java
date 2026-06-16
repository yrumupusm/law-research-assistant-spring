package com.example.lawassistant.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Entity
@Table(
        name = "articles",
        uniqueConstraints = @UniqueConstraint(name = "uq_article_law_number_current", columnNames = {"law_id", "article_number", "effective_to"})
)
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "law_id", nullable = false)
    private Law law;

    @Column(nullable = false, length = 40)
    private String articleNumber;

    @Column(length = 256)
    private String articleTitle;

    @Column(nullable = false, length = 1_000_000)
    private String content;

    @Column(nullable = false)
    private Integer orderIndex = 0;

    @Column(length = 64)
    private String contentHash;

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    @Column(length = 40)
    private String amendmentKind;

    @Column(name = "previous_article_id")
    private Long previousArticleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_version_id", nullable = false)
    private SnapshotVersion snapshotVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Article() {
    }

    public Article(Law law, String articleNumber, String articleTitle, String content, Integer orderIndex) {
        this.law = law;
        this.articleNumber = articleNumber;
        this.articleTitle = articleTitle;
        this.content = content;
        this.orderIndex = orderIndex;
        this.snapshotVersion = law.getSnapshotVersion();
        this.contentHash = sha256(content);
    }

    public Article(
            Law law,
            String articleNumber,
            String articleTitle,
            String content,
            Integer orderIndex,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String amendmentKind,
            Long previousArticleId
    ) {
        this(law, articleNumber, articleTitle, content, orderIndex);
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.amendmentKind = amendmentKind;
        this.previousArticleId = previousArticleId;
    }

    public Long getId() {
        return id;
    }

    public Law getLaw() {
        return law;
    }

    public String getArticleNumber() {
        return articleNumber;
    }

    public String getArticleTitle() {
        return articleTitle;
    }

    public String getContent() {
        return content;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getAmendmentKind() {
        return amendmentKind;
    }

    public Long getPreviousArticleId() {
        return previousArticleId;
    }

    public void closeAt(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalize(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lineNormalized = value
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        StringBuilder builder = new StringBuilder();
        int blankRun = 0;
        for (String line : lineNormalized.split("\n", -1)) {
            String strippedRight = line.stripTrailing();
            if (strippedRight.isBlank()) {
                blankRun++;
                if (blankRun > 1) {
                    continue;
                }
            } else {
                blankRun = 0;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(strippedRight);
        }
        return builder.toString();
    }
}
