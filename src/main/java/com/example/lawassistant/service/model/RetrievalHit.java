package com.example.lawassistant.service.model;

import com.example.lawassistant.domain.entity.Article;

public record RetrievalHit(
        Article article,
        double score,
        String reason
) {
}
