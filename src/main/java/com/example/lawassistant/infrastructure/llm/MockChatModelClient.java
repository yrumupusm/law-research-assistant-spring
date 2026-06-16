package com.example.lawassistant.infrastructure.llm;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockChatModelClient implements ChatModelClient {

    @Override
    public Map<String, Object> generateJson(List<ChatMessage> messages, String schemaName) {
        return Map.of(
                "schema", schemaName,
                "status", "mock",
                "messageCount", messages.size()
        );
    }
}
