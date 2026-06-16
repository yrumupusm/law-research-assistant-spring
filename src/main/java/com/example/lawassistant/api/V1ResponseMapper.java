package com.example.lawassistant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class V1ResponseMapper {

    private final ObjectMapper objectMapper;

    public V1ResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> toSnakeCaseMap(Object value) {
        Object converted = objectMapper.convertValue(value, Object.class);
        if (converted instanceof Map<?, ?> map) {
            return (Map<String, Object>) transformMap(map);
        }
        throw new IllegalArgumentException("V1 response must be an object.");
    }

    private Map<String, Object> transformMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = toSnakeCase(String.valueOf(entry.getKey()));
            target.put(key, transformValue(key, entry.getValue()));
        }
        return target;
    }

    private Object transformValue(String key, Object value) {
        if (value instanceof Map<?, ?> map) {
            return transformMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> transformed = new ArrayList<>(list.size());
            for (Object item : list) {
                transformed.add(transformValue(key, item));
            }
            return transformed;
        }
        if (value instanceof String text && isEnumLikeValue(key, text)) {
            return text.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private boolean isEnumLikeValue(String key, String value) {
        return switch (key) {
            case "status", "question_type", "law_type", "amendment_kind" -> isUpperSnake(value);
            default -> false;
        };
    }

    private boolean isUpperSnake(String value) {
        return !value.isBlank()
                && value.chars().allMatch(ch -> ch == '_' || Character.isDigit(ch) || Character.isUpperCase(ch));
    }

    private String toSnakeCase(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(current));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
}
