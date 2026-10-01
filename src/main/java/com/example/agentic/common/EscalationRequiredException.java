package com.example.agentic.common;

/** Thrown when automation has exhausted its options (e.g. repair loop) and a human must step in. */
public class EscalationRequiredException extends NonRetryableException {
    public EscalationRequiredException(String message) { super(message); }
}