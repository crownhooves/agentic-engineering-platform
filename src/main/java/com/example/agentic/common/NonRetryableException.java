package com.example.agentic.common;

/** Failures where retrying cannot help (bad config, invalid plan, missing recording). */
public class NonRetryableException extends RuntimeException {
    public NonRetryableException(String message) { super(message); }
    public NonRetryableException(String message, Throwable cause) { super(message, cause); }
}