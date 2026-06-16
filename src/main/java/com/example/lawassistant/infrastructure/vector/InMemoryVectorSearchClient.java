package com.example.lawassistant.infrastructure.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.vector.provider", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryVectorSearchClient implements VectorSearchClient {

    private final Map<String, List<VectorDocument>> collections = new HashMap<>();

    @Override
    public void upsert(String collectionName, List<VectorDocument> documents) {
        List<VectorDocument> collection = collections.computeIfAbsent(collectionName, ignored -> new ArrayList<>());
        Map<String, VectorDocument> merged = new HashMap<>();
        for (VectorDocument existing : collection) {
            merged.put(existing.id(), existing);
        }
        for (VectorDocument document : documents) {
            merged.put(document.id(), document);
        }
        collection.clear();
        collection.addAll(merged.values());
    }

    @Override
    public List<VectorSearchResult> search(String collectionName, List<Double> queryVector, int topK) {
        return collections.getOrDefault(collectionName, List.of()).stream()
                .map(document -> new VectorSearchResult(
                        document.id(),
                        cosine(queryVector, document.vector()),
                        document.metadata()
                ))
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    private double cosine(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        if (size == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < size; i++) {
            double l = left.get(i);
            double r = right.get(i);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
