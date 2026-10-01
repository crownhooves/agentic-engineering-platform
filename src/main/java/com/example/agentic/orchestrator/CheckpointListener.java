package com.example.agentic.orchestrator;

import com.example.agentic.domain.TaskNode;
import com.example.agentic.domain.TaskStatus;
import com.example.agentic.domain.repo.TaskNodeRepository;
import com.example.agentic.guardrails.AuditLogger;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checkpoints task state to H2 and writes audit events. No-op for steps that have no TaskNode row. */
public final class CheckpointListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(CheckpointListener.class);

    private final String runId;
    private final TaskNodeRepository nodes;
    private final AuditLogger audit;

    public CheckpointListener(String runId, TaskNodeRepository nodes, AuditLogger audit) {
        this.runId = runId;
        this.nodes = nodes;
        this.audit = audit;
    }

    @Override
    public void onStarted(TaskSpec task, int attempt) {
        update(task.key(), n -> {
            n.setStatus(TaskStatus.RUNNING);
            n.setAttempts(attempt);
            if (n.getStartedAt() == null) n.setStartedAt(Instant.now());
            n.setFinishedAt(null);
        });
        audit.log(runId, actor(task), "TASK_STARTED", task.key() + " attempt " + attempt);
    }

    @Override
    public void onRetry(TaskSpec task, int failedAttempt, Throwable error, Duration backoff) {
        update(task.key(), n -> n.setLastError(String.valueOf(error)));
        audit.log(runId, actor(task), "TASK_RETRY", task.key() + " attempt " + failedAttempt
                + " failed: " + error + "; retrying in " + backoff.toMillis() + " ms");
    }

    @Override
    public void onFinished(TaskSpec task, TaskResult result) {
        update(task.key(), n -> {
            n.setStatus(result.status());
            n.setAttempts(result.attempts());
            n.setFinishedAt(Instant.now());
            n.setLastError(result.status().isSuccess() ? null : result.message());
        });
        audit.log(runId, actor(task), "TASK_FINISHED",
                task.key() + " -> " + result.status() + " (" + result.message() + ")");
    }

    @Override
    public void onSkipped(TaskSpec task, String reason) {
        update(task.key(), n -> {
            n.setStatus(TaskStatus.SKIPPED);
            n.setLastError(reason);
        });
        audit.log(runId, "system", "TASK_SKIPPED", task.key() + ": " + reason);
    }

    private void update(String key, Consumer<TaskNode> change) {
        try {
            nodes.findByRunIdAndTaskKey(runId, key).ifPresent(n -> {
                change.accept(n);
                nodes.save(n);
            });
        } catch (RuntimeException e) {
            log.warn("Could not checkpoint task {} of run {}", key, runId, e);
        }
    }

    private static String actor(TaskSpec task) { return task.agent().name().toLowerCase(Locale.ROOT); }
}