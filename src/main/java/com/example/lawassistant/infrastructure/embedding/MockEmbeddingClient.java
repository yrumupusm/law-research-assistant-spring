package com.example.lawassistant.infrastructure.embedding;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmbeddingClient implements EmbeddingClient {

    private static final int DIMENSIONS = 8;

    @Override
    public List<Double> embed(String text) {
        double[] vector = new double[DIMENSIONS];
        String value = text == null ? "" : text.toLowerCase();
        for (int i = 0; i < value.length(); i++) {
            vector[i % DIMENSIONS] += value.charAt(i);
        }
        List<Double> result = new ArrayList<>();
        for (double v : vector) {
            result.add(v == 0.0 ? 0.0 : v / 1000.0);
        }
        return result;
    }

    @Override
    public List<List<Double>> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
