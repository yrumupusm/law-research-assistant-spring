package com.example.lawassistant.infrastructure.vector;

import java.util.List;

public interface VectorSearchClient {

    void upsert(String collectionName, List<VectorDocument> documents);

    List<VectorSearchResult> search(String collectionName, List<Double> queryVector, int topK);
}
