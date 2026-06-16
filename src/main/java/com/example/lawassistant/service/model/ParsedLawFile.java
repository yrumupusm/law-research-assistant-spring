package com.example.lawassistant.service.model;

import com.example.lawassistant.domain.enums.LawType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ParsedLawFile(
        String title,
        LawType lawType,
        String lawNumber,
        LocalDate enactedAt,
        LocalDate lastAmendedAt,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String sourcePath,
        Map<String, String> metadata,
        List<ParsedLawArticle> articles
) {
}
