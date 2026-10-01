package com.example.agentic.orchestrator;

import com.example.agentic.domain.ArtifactType;
import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.guardrails.AuditLogger;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The only way past a human gate. Each method checks the run is in the exact expected state, performs the
 * transition synchronously (so a double click cannot start work twice), then hands off to the engine.
 */
@Service
public class ApprovalService {

    private final RunLifecycle lifecycle;
    private final RunContext context;
    private final WorkflowEngine engine;
    private final AuditLogger audit;

    public ApprovalService(RunLifecycle lifecycle, RunContext context, WorkflowEngine engine, AuditLogger audit) {
        this.lifecycle = lifecycle;
        this.context = context;
        this.engine = engine;
        this.audit = audit;
    }

    /** Gate 1: answers to blocking questions (an empty map means "go with your stated assumptions"). */
    public void submitClarifications(String runId, Map<String, String> answers) {
        expect(runId, RunStatus.AWAITING_CLARIFICATION);
        lifecycle.transition(runId, RunStatus.PLANNING);
        Map<String, String> safe = answers == null ? Map.of() : answers;
        context.putJson(runId, "human", ArtifactType.CLARIFICATION_ANSWERS, "clarifications.json", safe);
        audit.log(runId, "human", "CLARIFICATIONS_SUBMITTED", safe.size() + " answer(s)");
        engine.planAsync(runId);
    }

    /** Gate 2: approve the plan, optionally removing tasks first. */
    public void approvePlan(String runId, Set<String> removedTaskKeys) {
        expect(runId, RunStatus.AWAITING_PLAN_APPROVAL);
        Set<String> removed = removedTaskKeys == null ? Set.of() : removedTaskKeys;
        if (!removed.isEmpty()) lifecycle.removeTasks(runId, removed);     // validates before we commit
        lifecycle.transition(runId, RunStatus.EXECUTING);
        audit.log(runId, "human", "PLAN_APPROVED", "removed tasks: " + removed);
        engine.executeAsync(runId);
    }

    /** Gate 3: accept or reject the final artifacts. */
    public void approveResult(String runId) {
        expect(runId, RunStatus.AWAITING_REVIEW);
        lifecycle.transition(runId, RunStatus.COMPLETED);
        audit.log(runId, "human", "RESULT_APPROVED", "");
    }

    public void rejectResult(String runId, String reason) {
        expect(runId, RunStatus.AWAITING_REVIEW);
        lifecycle.transition(runId, RunStatus.REJECTED);
        audit.log(runId, "human", "RESULT_REJECTED", reason == null ? "" : reason);
    }

    /** Marks the run CANCELLED. Tasks already executing finish in the background; their results are ignored. */
    public void cancel(String runId) {
        lifecycle.transition(runId, RunStatus.CANCELLED);
        audit.log(runId, "human", "RUN_CANCELLED", "");
    }

    private void expect(String runId, RunStatus expected) {
        Run run = lifecycle.get(runId);
        if (run.getStatus() != expected) {
            throw new IllegalStateException("Run " + runId + " is " + run.getStatus() + ", expected " + expected);
        }
    }
}