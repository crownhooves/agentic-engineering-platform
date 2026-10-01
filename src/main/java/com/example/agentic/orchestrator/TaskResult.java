package com.example.agentic.orchestrator;

import com.example.agentic.domain.TaskStatus;
import java.time.Duration;

public record TaskResult(TaskStatus status, int attempts, String message, Duration duration) {
    public static TaskResult succeeded(int attempts, Duration d) {
        return new TaskResult(TaskStatus.SUCCEEDED, attempts, "OK", d);
    }
    public static TaskResult failed(int attempts, String msg, Duration d) {
        return new TaskResult(TaskStatus.FAILED, attempts, msg, d);
    }
    public static TaskResult escalated(int attempts, String msg, Duration d) {
        return new TaskResult(TaskStatus.ESCALATED, attempts, msg, d);
    }
    public static TaskResult skipped(String msg) {
        return new TaskResult(TaskStatus.SKIPPED, 0, msg, Duration.ZERO);
    }
    public static TaskResult alreadyDone() {
        return new TaskResult(TaskStatus.SUCCEEDED, 0, "Completed in a previous execution (resume)", Duration.ZERO);
    }
}