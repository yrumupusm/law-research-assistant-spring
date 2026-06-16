package com.example.lawassistant.service.model;

import java.util.List;

public record VectorIndexResult(
        int totalArticles,
        int indexedArticles,
        List<Long> failedArticleIds
) {

    public VectorIndexResult {
        failedArticleIds = failedArticleIds == null ? List.of() : List.copyOf(failedArticleIds);
    }

    public int failedArticles() {
        return failedArticleIds.size();
    }

    public boolean hasFailures() {
        return failedArticles() > 0;
    }

    public String failureSummary() {
        if (!hasFailures()) {
            return null;
        }
        return "Vector reindex partially failed: indexed "
                + indexedArticles
                + "/"
                + totalArticles
                + ", failed articleIds="
                + failedArticleIds;
    }
}
