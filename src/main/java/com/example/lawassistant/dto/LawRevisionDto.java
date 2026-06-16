package com.example.lawassistant.dto;

import java.time.LocalDate;

public record LawRevisionDto(
        LocalDate effectiveFrom,
        String amendmentKind,
        long articleCount
) {
}
