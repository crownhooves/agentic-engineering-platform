package com.example.agentic.guardrails;

import com.example.agentic.common.NonRetryableException;

public class GuardrailViolationException extends NonRetryableException {
    public GuardrailViolationException(String message) { super(message); }
}