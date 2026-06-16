package com.example.lawassistant.dto;

import java.util.List;

public record LawRevisionListResponse(
        Long lawId,
        String lawTitle,
        List<LawRevisionDto> revisions
) {
}
