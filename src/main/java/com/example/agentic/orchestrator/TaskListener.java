package com.example.agentic.orchestrator;

import java.time.Duration;

/**
 * Hooks for checkpointing, SSE events and metrics. Called from worker threads,
 * so implementations must be thread-safe. Exceptions thrown here are swallowed and logged.
 */
public interface TaskListener {
    default void onStarted(TaskSpec task, int attempt) {}
    default void onRetry(TaskSpec task, int failedAttempt, Throwable error, Duration backoff) {}
    default void onFinished(TaskSpec task, TaskResult result) {}
    default void onSkipped(TaskSpec task, String reason) {}

    TaskListener NOOP = new TaskListener() {};
}