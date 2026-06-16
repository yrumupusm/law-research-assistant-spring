package com.example.lawassistant.infrastructure.llm;

import java.util.List;
import java.util.Map;

public interface ChatModelClient {

    Map<String, Object> generateJson(List<ChatMessage> messages, String schemaName);
}
