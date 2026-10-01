package com.example.agentic.orchestrator;

import com.example.agentic.domain.TaskStatus;
import java.util.List;
import java.util.Map;

public record ExecutionReport(Map<String, TaskResult> results) {

    public boolean allSucceeded() {
        return results.values().stream().allMatch(r -> r.status() == TaskStatus.SUCCEEDED);
    }

    /** FAILED or ESCALATED task keys. */
    public List<String> failedKeys() {
        return results.entrySet().stream()
                .filter(e -> e.getValue().status() == TaskStatus.FAILED
                        || e.getValue().status() == TaskStatus.ESCALATED)
                .map(Map.Entry::getKey).toList();
    }

    public List<String> skippedKeys() {
        return results.entrySet().stream()
                .filter(e -> e.getValue().status() == TaskStatus.SKIPPED)
                .map(Map.Entry::getKey).toList();
    }
}