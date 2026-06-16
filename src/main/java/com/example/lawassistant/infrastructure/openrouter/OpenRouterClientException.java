package com.example.lawassistant.infrastructure.openrouter;

public class OpenRouterClientException extends RuntimeException {

    public OpenRouterClientException(String message) {
        super(message);
    }

    public OpenRouterClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
