package com.example.agentic.guardrails;

import com.example.agentic.common.EscalationRequiredException;

/** Escalation, not failure: a human decides whether to raise the budget and resume. */
public class BudgetExceededException extends EscalationRequiredException {
    public BudgetExceededException(String message) { super(message); }
}