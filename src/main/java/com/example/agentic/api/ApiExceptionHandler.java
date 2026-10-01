package com.example.agentic.api;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> notFound(NoSuchElementException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiError(
                        "NOT_FOUND",
                        e.getMessage(),
                        Instant.now()));
    }

    @ExceptionHandler({
            IllegalStateException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> conflict(RuntimeException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiError(
                        "INVALID_OPERATION",
                        e.getMessage(),
                        Instant.now()));
    }

    public record ApiError(
            String code,
            String message,
            Instant timestamp) {
    }
}