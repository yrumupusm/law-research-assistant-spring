package com.example.lawassistant.dto;

import java.util.List;

public record AgentTraceListResponse(
        long total,
        List<AgentTraceItemDto> items
) {
}
