package com.example.agentic.orchestrator;

import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.guardrails.AuditLogger;
import com.example.agentic.tools.RuntimeVerificationResult;
import com.example.agentic.tools.RuntimeVerificationService;
import com.example.agentic.tools.WorkspaceManager;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic post-DAG validation step.
 *
 * <p>The agent DAG produces source and test artifacts. This component
 * materializes the current source/test tree into the isolated run workspace,
 * invokes deterministic runtime verification, and persists the verification
 * result as reviewable evidence.
 *
 * <p>This is intentionally outside the LLM task DAG. Runtime verification is
 * infrastructure validation, not an agent task.
 */
@Component
public class RuntimeVerificationStep {

    private final RunContext context;
    private final WorkspaceManager workspaceManager;
    private final RuntimeVerificationService runtimeVerificationService;
    private final AuditLogger audit;

    public RuntimeVerificationStep(
            RunContext context,
            WorkspaceManager workspaceManager,
            RuntimeVerificationService runtimeVerificationService,
            AuditLogger audit) {
        this.context = context;
        this.workspaceManager = workspaceManager;
        this.runtimeVerificationService = runtimeVerificationService;
        this.audit = audit;
    }

    public RuntimeVerificationResult verify(String runId) {

        /*
         * Obtain the latest SOURCE_CODE and TEST_CODE artifacts produced
         * by the agent DAG.
         */
        List<Artifact> files =
                context.currentFiles(
                        runId,
                        ArtifactType.SOURCE_CODE,
                        ArtifactType.TEST_CODE);

        if (files.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot perform runtime verification: "
                            + "no source or test artifacts exist for run "
                            + runId);
        }

        /*
         * Materialize the latest generated source/test artifacts into the
         * isolated run workspace.
         *
         * RuntimeVerificationService subsequently resolves this same
         * workspace through workspaceManager.workspace(runId).
         */
        workspaceManager.materialize(
                runId,
                files);

        /*
         * Batch 6A owns the actual runtime verification contract:
         *
         *     verify(String runId)
         *
         * The service resolves the workspace itself, injects its temporary
         * launcher, compiles the application, starts it, executes the
         * HTTP checks, and cleans up the launcher/process.
         */
        RuntimeVerificationResult result =
                runtimeVerificationService.verify(runId);

        String detail =
                result.passed()
                        ? "Runtime verification passed on port "
                        + result.port()
                        + " with "
                        + result.checks().size()
                        + " checks"
                        : "Runtime verification failed: "
                        + result.failureReason();

        audit.log(
                runId,
                "system",
                result.passed()
                        ? "RUNTIME_VERIFICATION_PASSED"
                        : "RUNTIME_VERIFICATION_FAILED",
                detail);

        /*
         * Persist structured runtime evidence so it becomes part of the
         * reviewable engineering output and is available to the UI.
         */
        context.putJson(
                runId,
                "runtime-verification",
                ArtifactType.RUNTIME_VERIFICATION,
                "runtime-verification.json",
                result);

        return result;
    }
}
