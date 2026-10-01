package com.example.agentic.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler =
            new ApiExceptionHandler();

    @Test
    void notFoundReturns404() {
        NoSuchElementException exception =
                new NoSuchElementException("Run not found");

        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.notFound(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                "NOT_FOUND",
                response.getBody().code()
        );

        assertEquals(
                "Run not found",
                response.getBody().message()
        );

        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void illegalStateExceptionReturns409() {
        IllegalStateException exception =
                new IllegalStateException("Run cannot be resumed");

        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.conflict(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                "INVALID_OPERATION",
                response.getBody().code()
        );

        assertEquals(
                "Run cannot be resumed",
                response.getBody().message()
        );

        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void illegalArgumentExceptionReturns409() {
        IllegalArgumentException exception =
                new IllegalArgumentException("Invalid task key");

        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.conflict(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(
                "INVALID_OPERATION",
                response.getBody().code()
        );

        assertEquals(
                "Invalid task key",
                response.getBody().message()
        );

        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void errorMessageCanBeNull() {
        NoSuchElementException exception =
                new NoSuchElementException();

        ResponseEntity<ApiExceptionHandler.ApiError> response =
                handler.notFound(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals("NOT_FOUND", response.getBody().code());
        assertNull(response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }
}