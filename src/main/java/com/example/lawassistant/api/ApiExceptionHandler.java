package com.example.lawassistant.api;

import com.example.lawassistant.infrastructure.openrouter.OpenRouterClientException;
import com.example.lawassistant.infrastructure.git.GitCommandException;
import com.example.lawassistant.service.AdminOperationDisabledException;
import java.util.Map;
import com.example.lawassistant.infrastructure.rerank.RerankerClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException exception) {
        return Map.of("error", "invalid request");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return Map.of("error", "invalid request");
    }

    @ExceptionHandler(AdminOperationDisabledException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleAdminOperationDisabled(AdminOperationDisabledException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(OpenRouterClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleProviderFailure(OpenRouterClientException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(GitCommandException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleGitFailure(GitCommandException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(RerankerClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleRerankerFailure(RerankerClientException exception) {
        return Map.of("error", exception.getMessage());
    }
}
