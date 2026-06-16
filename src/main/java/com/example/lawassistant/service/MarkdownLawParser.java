package com.example.lawassistant.service;

import com.example.lawassistant.domain.enums.LawType;
import com.example.lawassistant.service.model.ParsedLawArticle;
import com.example.lawassistant.service.model.ParsedLawFile;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MarkdownLawParser {

    private static final Pattern ARTICLE_HEADER = Pattern.compile(
            "^#####\\s+(제\\d+조(?:의\\d+)?)(?:\\s*\\(([^\\)\\n]*?)\\))?.*$"
    );
    private static final Pattern ARTICLE_BASE_NUMBER = Pattern.compile("^(제\\d+조)");

    public ParsedLawFile parse(String text, String sourcePath) {
        ParsedDocument document = splitFrontmatter(text);
        Map<String, String> metadata = document.metadata();
        if ("폐지".equals(metadata.get("상태"))) {
            return null;
        }

        String title = metadata.getOrDefault("제목", "").strip();
        LawType lawType = resolveLawType(metadata.get("법령구분"));
        if (title.isBlank() || lawType == null) {
            return null;
        }

        List<ParsedLawArticle> articles = extractArticles(document.body());
        if (articles.isEmpty()) {
            return null;
        }

        return new ParsedLawFile(
                title,
                lawType,
                blankToNull(metadata.get("법령MST")),
                parseDate(metadata.get("공포일자")),
                parseDate(metadata.get("개정일자")),
                parseDate(metadata.get("시행일자")),
                parseDate(metadata.get("효력종료일자")),
                sourcePath,
                metadata,
                articles
        );
    }

    private ParsedDocument splitFrontmatter(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.startsWith("---\n")) {
            return new ParsedDocument(Map.of(), normalized);
        }

        int end = normalized.indexOf("\n---", 4);
        if (end < 0) {
            return new ParsedDocument(Map.of(), normalized);
        }

        String frontmatter = normalized.substring(4, end);
        int bodyStart = normalized.indexOf('\n', end + 4);
        String body = bodyStart < 0 ? "" : normalized.substring(bodyStart + 1);
        return new ParsedDocument(parseSimpleYaml(frontmatter), body);
    }

    private Map<String, String> parseSimpleYaml(String frontmatter) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (String line : frontmatter.split("\n")) {
            if (!line.isBlank() && Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            String trimmed = line.strip();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).strip();
            String value = trimmed.substring(colon + 1).strip();
            metadata.putIfAbsent(key, stripQuotes(value));
        }
        return metadata;
    }

    private List<ParsedLawArticle> extractArticles(String body) {
        List<ParsedLawArticle> articles = new ArrayList<>();
        String currentNumber = null;
        String currentTitle = null;
        List<String> currentLines = new ArrayList<>();
        Set<String> usedNumbers = new HashSet<>();
        int order = 0;

        for (String line : body.split("\n")) {
            Matcher matcher = ARTICLE_HEADER.matcher(line);
            if (matcher.matches()) {
                if (currentNumber != null) {
                    articles.add(toArticle(currentNumber, currentTitle, currentLines, order));
                }
                order++;
                currentNumber = uniqueArticleNumber(matcher.group(1).strip(), usedNumbers);
                currentTitle = blankToNull(matcher.group(2));
                currentLines = new ArrayList<>();
                continue;
            }
            if (currentNumber != null) {
                currentLines.add(line);
            }
        }

        if (currentNumber != null) {
            articles.add(toArticle(currentNumber, currentTitle, currentLines, order));
        }
        return articles;
    }

    private String uniqueArticleNumber(String rawNumber, Set<String> usedNumbers) {
        if (usedNumbers.add(rawNumber)) {
            return rawNumber;
        }
        String baseNumber = baseArticleNumber(rawNumber);
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String candidate = baseNumber + "의" + suffix;
            if (usedNumbers.add(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("too many duplicate article numbers: " + baseNumber);
    }

    private String baseArticleNumber(String articleNumber) {
        Matcher matcher = ARTICLE_BASE_NUMBER.matcher(articleNumber);
        return matcher.find() ? matcher.group(1) : articleNumber;
    }

    private ParsedLawArticle toArticle(String number, String title, List<String> lines, int order) {
        return new ParsedLawArticle(number, title, String.join("\n", lines).strip(), order);
    }

    private LawType resolveLawType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String kind = value.strip();
        if ("법률".equals(kind)) {
            return LawType.LAW;
        }
        if ("시행령".equals(kind) || "대통령령".equals(kind)) {
            return LawType.ENFORCEMENT_DECREE;
        }
        if ("시행규칙".equals(kind) || "총리령".equals(kind) || kind.endsWith("부령")) {
            return LawType.ENFORCEMENT_RULE;
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid law metadata date: " + value, ex);
        }
    }

    private String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private record ParsedDocument(Map<String, String> metadata, String body) {
    }
}
