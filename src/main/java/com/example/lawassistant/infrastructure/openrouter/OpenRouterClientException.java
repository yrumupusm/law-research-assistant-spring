package com.example.lawassistant.infrastructure.openrouter;

public class OpenRouterClientException extends RuntimeException {

    private final String failureCode;

    public OpenRouterClientException(String message) {
        this(message, "provider_response_invalid", null);
    }

    public OpenRouterClientException(String message, Throwable cause) {
        this(message, "provider_request_failed", cause);
    }

    public OpenRouterClientException(String message, String failureCode, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
