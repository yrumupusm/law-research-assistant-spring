package com.example.lawassistant.service.model;

public record ParsedLawArticle(
        String articleNumber,
        String articleTitle,
        String content,
        int orderIndex
) {
}
