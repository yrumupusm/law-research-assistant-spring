package com.example.lawassistant.service;

import com.example.lawassistant.domain.entity.SearchLog;
import com.example.lawassistant.domain.enums.QuestionType;
import com.example.lawassistant.domain.enums.SearchStatus;
import com.example.lawassistant.repository.SearchLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SearchLogAgent {

    private static final int PREVIEW_LIMIT = 48;
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(api\\s*key|api[_-]?key|token|secret|password|비밀번호|토큰)\\s*[:=]\\s*\\S+"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://\\S+|www\\.\\S+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LONG_NUMBER_PATTERN = Pattern.compile("\\b\\d{6,}\\b");

    private final SearchLogRepository repository;

    public SearchLogAgent(SearchLogRepository repository) {
        this.repository = repository;
    }

    public void save(
            String requestId,
            String question,
            QuestionType questionType,
            SearchStatus status,
            double confidence,
            String snapshotVersion,
            LocalDate asOf,
            int citedArticleCount,
            int analyzeMs,
            int retrieveMs,
            int synthesizeMs
    ) {
        repository.save(new SearchLog(
                requestId,
                sha256(question),
                preview(question),
                question.length(),
                questionType,
                status,
                confidence,
                snapshotVersion,
                asOf,
                citedArticleCount,
                analyzeMs,
                retrieveMs,
                synthesizeMs
        ));
    }

    private String preview(String question) {
        String normalized = question == null ? "" : question.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }
        String redacted = redact(normalized);
        if (redacted.length() == 1) {
            return "...";
        }
        int visibleLength = Math.min(PREVIEW_LIMIT, redacted.length() - 1);
        return redacted.substring(0, visibleLength) + "...";
    }

    private String redact(String value) {
        String redacted = CREDENTIAL_PATTERN.matcher(value).replaceAll("$1=[REDACTED]");
        redacted = URL_PATTERN.matcher(redacted).replaceAll("[REDACTED_URL]");
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
        redacted = LONG_NUMBER_PATTERN.matcher(redacted).replaceAll("[REDACTED_NUMBER]");
        return redacted;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
