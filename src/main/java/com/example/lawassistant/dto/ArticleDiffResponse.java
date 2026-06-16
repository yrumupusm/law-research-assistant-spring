package com.example.lawassistant.dto;

public record ArticleDiffResponse(
        Long articleIdA,
        Long articleIdB,
        Long lawId,
        String lawTitle,
        String articleNumber,
        String contentA,
        String contentB,
        String contentHashA,
        String contentHashB,
        boolean contentHashEqual
) {
}
