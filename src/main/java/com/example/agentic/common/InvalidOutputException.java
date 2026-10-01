package com.example.agentic.common;

/** The model's output was not parseable / did not satisfy the DTO's validation. */
public class InvalidOutputException extends RuntimeException {
    public InvalidOutputException(String message) { super(message); }
    public InvalidOutputException(String message, Throwable cause) { super(message, cause); }
}