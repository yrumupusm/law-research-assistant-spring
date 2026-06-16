package com.example.lawassistant.infrastructure.llm;

public record ChatMessage(
        String role,
        String content
) {
}
