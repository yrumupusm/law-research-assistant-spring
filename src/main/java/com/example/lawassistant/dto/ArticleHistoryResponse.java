package com.example.lawassistant.dto;

import java.util.List;

public record ArticleHistoryResponse(
        Long lawId,
        String lawTitle,
        String articleNumber,
        List<ArticleHistoryEntryDto> entries
) {
}
