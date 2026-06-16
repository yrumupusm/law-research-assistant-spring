package com.example.lawassistant.service.model;

import java.util.List;

public record RetrievalResult(
        List<RetrievalHit> hits,
        int keywordHits,
        int vectorHits,
        int mergedHits
) {
}
