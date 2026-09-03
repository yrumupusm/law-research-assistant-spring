package com.example.lawassistant.infrastructure.vector;

import java.util.List;

public interface VectorSearchClient {

    long count(String collectionName);

    void upsert(String collectionName, List<VectorDocument> documents);

    List<VectorSearchResult> search(String collectionName, List<Double> queryVector, int topK);
}
