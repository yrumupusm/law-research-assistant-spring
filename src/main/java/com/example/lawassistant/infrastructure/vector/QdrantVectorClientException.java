package com.example.lawassistant.infrastructure.vector;

public class QdrantVectorClientException extends RuntimeException {

    public QdrantVectorClientException(String message) {
        super(message);
    }

    public QdrantVectorClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
