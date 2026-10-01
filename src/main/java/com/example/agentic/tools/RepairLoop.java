package com.example.agentic.tools;

import com.example.agentic.agents.AgentContext;
import com.example.agentic.common.EscalationRequiredException;
import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.orchestrator.RunContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Closed-loop validation controller: materialize -> compile/test -> record evidence -> repair -> repeat.
 *
 * <p>The loop never edits files directly from compiler output. A repair agent receives the failure
 * as context and must emit another validated file bundle. After the configured repair budget is
 * exhausted the task escalates instead of silently accepting a broken build.</p>
 */
@Component
public final class RepairLoop {

    @FunctionalInterface
    public interface RepairClient {
        void repair(AgentContext context, BuildResult failure, int repairAttempt) throws Exception;
    }

    @FunctionalInterface
    public interface ValidationRunner {
        BuildResult validate(java.nio.file.Path workspace);
    }

    private final WorkspaceManager workspaceManager;
    private final ValidationRunner runner;
    private final RunContext context;
    private final RepairClient repairClient;
    private final int maxRepairs;

    /**
     * Production constructor used by Spring.
     *
     * <p>The explicit {@link Autowired} annotation is required because this class also exposes
     * a second constructor for deterministic unit testing.</p>
     */
    @Autowired
    public RepairLoop(WorkspaceManager workspaceManager,
                      CompileAndTestRunner runner,
                      RunContext context,
                      RepairClient repairClient) {
        this(workspaceManager, runner::validate, context, repairClient, 2);
    }

    /**
     * Test seam: inject a deterministic validator and repair budget.
     */
    public RepairLoop(WorkspaceManager workspaceManager,
                      ValidationRunner runner,
                      RunContext context,
                      RepairClient repairClient,
                      int maxRepairs) {
        if (maxRepairs < 0) {
            throw new IllegalArgumentException("maxRepairs must be >= 0");
        }

        this.workspaceManager = workspaceManager;
        this.runner = runner;
        this.context = context;
        this.repairClient = repairClient;
        this.maxRepairs = maxRepairs;
    }

    /**
     * Executes the closed-loop validation and repair workflow.
     *
     * <p>The workflow performs an initial validation and then allows a bounded number of repair
     * attempts. Every validation result is persisted as a validation artifact. If validation
     * remains unsuccessful after the repair budget is exhausted, the workflow escalates rather
     * than accepting a broken build.</p>
     *
     * @param runId identifier of the current workflow run
     * @param agentContext context of the task requesting validation
     * @return successful build result
     * @throws Exception if the repair agent fails or validation ultimately requires escalation
     */
    public BuildResult validateAndRepair(String runId, AgentContext agentContext) throws Exception {
        List<Artifact> artifacts = context.currentFiles(
                runId,
                ArtifactType.SOURCE_CODE,
                ArtifactType.TEST_CODE);

        workspaceManager.prepare(runId);
        workspaceManager.materialize(runId, artifacts);

        BuildResult result = runner.validate(workspaceManager.workspace(runId));
        persist(runId, agentContext.taskKey(), result);

        for (int repair = 1; !result.success() && repair <= maxRepairs; repair++) {
            repairClient.repair(agentContext, result, repair);

            artifacts = context.currentFiles(
                    runId,
                    ArtifactType.SOURCE_CODE,
                    ArtifactType.TEST_CODE);

            workspaceManager.prepare(runId);
            workspaceManager.materialize(runId, artifacts);

            result = runner.validate(workspaceManager.workspace(runId));
            persist(runId, agentContext.taskKey(), result);
        }

        if (!result.success()) {
            throw new EscalationRequiredException(
                    "Validation failed after "
                            + maxRepairs
                            + " repair attempt(s): "
                            + summarize(result));
        }

        return result;
    }

    /**
     * Persists the validation result as a structured JSON artifact.
     */
    private void persist(String runId, String taskKey, BuildResult result) {
        String content = """
                {
                  "success": %s,
                  "compileSucceeded": %s,
                  "testsExecuted": %s,
                  "exitCode": %d,
                  "testsRun": %d,
                  "failures": %d,
                  "durationMs": %d,
                  "output": %s
                }
                """.formatted(
                result.success(),
                result.compileSucceeded(),
                result.testsExecuted(),
                result.exitCode(),
                result.testsRun(),
                result.failures(),
                result.duration().toMillis(),
                quote(result.output()));

        context.put(
                runId,
                taskKey,
                ArtifactType.VALIDATION_REPORT,
                "validation-report.json",
                content);
    }

    /**
     * Produces a bounded failure summary suitable for escalation messages.
     */
    private static String summarize(BuildResult result) {
        String output = result.output() == null
                ? ""
                : result.output().strip();

        if (output.length() > 1_500) {
            output = output.substring(0, 1_500) + "...";
        }

        return output.replace('\n', ' ');
    }

    /**
     * Escapes a string for safe embedding in the validation JSON document.
     */
    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }

        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");

        return "\"" + escaped + "\"";
    }
}