package com.example.agentic.orchestrator;

@FunctionalInterface
public interface TaskHandler {
    /** Do the work. Throw NonRetryableException / EscalationRequiredException to bypass retries. */
    void execute(TaskSpec task, int attempt) throws Exception;
}