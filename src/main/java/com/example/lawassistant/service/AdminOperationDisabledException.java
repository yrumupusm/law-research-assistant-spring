package com.example.lawassistant.service;

public class AdminOperationDisabledException extends RuntimeException {

    public AdminOperationDisabledException(String message) {
        super(message);
    }
}
