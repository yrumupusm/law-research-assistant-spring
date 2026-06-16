package com.example.lawassistant.infrastructure.rerank;

public class RerankerClientException extends RuntimeException {

    public RerankerClientException(String message) {
        super(message);
    }

    public RerankerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
