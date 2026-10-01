package com.example.agentic.orchestrator;

import com.example.agentic.agents.*;
import com.example.agentic.agents.dto.AnalysisOutput;
import com.example.agentic.agents.dto.PlanOutput;
import com.example.agentic.common.NonRetryableException;
import com.example.agentic.domain.*;
import com.example.agentic.domain.repo.TaskNodeRepository;
import com.example.agentic.guardrails.AuditLogger;
import com.example.agentic.guardrails.BudgetGuard;
import com.example.agentic.tools.RuntimeVerificationResult;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Drives a run:
 *
 * <pre>
 * analyze
 *   -> (clarify)
 *   -> plan
 *   -> [human approves]
 *   -> execute DAG
 *   -> runtime verification
 *   -> [human reviews]
 * </pre>
 *
 * <p>Human gates are separate methods on ApprovalService; this class never
 * approves anything itself.
 *
 * <p>Runtime verification is deliberately performed outside the agent DAG.
 * The DAG is responsible for engineering work and artifact generation, while
 * runtime verification is deterministic infrastructure validation.
 */
@Service
public class WorkflowEngine {

    private static final Logger log =
            LoggerFactory.getLogger(WorkflowEngine.class);

    public static class StepFailedException extends RuntimeException {

        public StepFailedException(String message) {
            super(message);
        }
    }

    private final RunLifecycle lifecycle;
    private final RunContext context;
    private final RequirementAnalystAgent analyst;
    private final PlannerAgent planner;
    private final Map<AgentType, Agent> agents;
    private final DagExecutor dagExecutor;
    private final TaskRunner taskRunner;
    private final TaskNodeRepository nodes;
    private final AuditLogger audit;
    private final BudgetGuard budget;
    private final ExecutorService workflowPool;
    private final RuntimeVerificationStep runtimeVerificationStep;

    public WorkflowEngine(
            RunLifecycle lifecycle,
            RunContext context,
            RequirementAnalystAgent analyst,
            PlannerAgent planner,
            List<Agent> agentList,
            DagExecutor dagExecutor,
            TaskRunner taskRunner,
            TaskNodeRepository nodes,
            AuditLogger audit,
            BudgetGuard budget,
            @Qualifier("workflowPool") ExecutorService workflowPool,
            RuntimeVerificationStep runtimeVerificationStep) {

        this.lifecycle = lifecycle;
        this.context = context;
        this.analyst = analyst;
        this.planner = planner;
        this.dagExecutor = dagExecutor;
        this.taskRunner = taskRunner;
        this.nodes = nodes;
        this.audit = audit;
        this.budget = budget;
        this.workflowPool = workflowPool;
        this.runtimeVerificationStep = runtimeVerificationStep;

        Map<AgentType, Agent> map =
                new EnumMap<>(AgentType.class);

        for (Agent agent : agentList) {
            if (map.put(agent.type(), agent) != null) {
                throw new IllegalStateException(
                        "Duplicate agent for " + agent.type());
            }
        }

        this.agents = map;
    }

    /**
     * Creates the run and starts analysis in the background; returns immediately.
     */
    public Run start(String requirement, String scenario) {
        String sc =
                (scenario == null || scenario.isBlank())
                        ? "custom"
                        : scenario;

        Run run = lifecycle.create(requirement, sc);

        lifecycle.transition(
                run.getId(),
                RunStatus.ANALYZING);

        submit(
                run.getId(),
                () -> analyzeAndPlan(run.getId()));

        return lifecycle.get(run.getId());
    }

    /**
     * Resume a FAILED run from where it stopped.
     *
     * <p>Grants a fresh budget window: that is the human decision.
     */
    public void resume(String runId) {
        Run run = lifecycle.get(runId);

        if (run.getStatus() != RunStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED runs can be resumed (status: "
                            + run.getStatus()
                            + ")");
        }

        budget.release(runId);

        audit.log(
                runId,
                "human",
                "RUN_RESUMED",
                "previous failure: "
                        + run.getFailureReason());

        if (!lifecycle.tasks(runId).isEmpty()) {

            int reset =
                    lifecycle.resetForResume(runId);

            audit.log(
                    runId,
                    "system",
                    "TASKS_RESET",
                    reset
                            + " task(s) back to PENDING; "
                            + "succeeded tasks are kept");

            lifecycle.transition(
                    runId,
                    RunStatus.EXECUTING);

            executeAsync(runId);

        } else if (context.latest(
                runId,
                ArtifactType.REQUIREMENT_ANALYSIS).isPresent()) {

            lifecycle.transition(
                    runId,
                    RunStatus.PLANNING);

            planAsync(runId);

        } else {

            lifecycle.transition(
                    runId,
                    RunStatus.ANALYZING);

            submit(
                    runId,
                    () -> analyzeAndPlan(runId));
        }
    }

    void planAsync(String runId) {
        submit(
                runId,
                () -> planAndAwaitApproval(runId));
    }

    void executeAsync(String runId) {
        submit(
                runId,
                () -> executePlan(runId));
    }

    // ---- steps -------------------------------------------------------------------------------

    private void analyzeAndPlan(String runId) {
        Run run = lifecycle.get(runId);

        AnalysisOutput analysis =
                step(
                        runId,
                        "analyze",
                        AgentType.REQUIREMENT_ANALYST,
                        () -> analyst.analyze(run));

        context.putJson(
                runId,
                "analyze",
                ArtifactType.REQUIREMENT_ANALYSIS,
                "analysis.json",
                analysis);

        if (!analysis.blockingQuestions().isEmpty()) {
            lifecycle.transition(
                    runId,
                    RunStatus.AWAITING_CLARIFICATION);
            return;
        }

        lifecycle.transition(
                runId,
                RunStatus.PLANNING);

        planAndAwaitApproval(runId);
    }

    private void planAndAwaitApproval(String runId) {
        Run run = lifecycle.get(runId);

        PlanOutput plan =
                step(
                        runId,
                        "plan",
                        AgentType.PLANNER,
                        () -> planner.plan(run));

        List<TaskNode> planned =
                plan.tasks()
                        .stream()
                        .map(
                                task ->
                                        new TaskNode(
                                                runId,
                                                task.key(),
                                                task.title(),
                                                task.description(),
                                                task.agent(),
                                                new LinkedHashSet<>(
                                                        task.dependsOn())))
                        .toList();

        lifecycle.replacePlan(
                runId,
                planned);

        context.putJson(
                runId,
                "plan",
                ArtifactType.PLAN,
                "plan.json",
                plan);

        lifecycle.transition(
                runId,
                RunStatus.AWAITING_PLAN_APPROVAL);
    }

    /**
     * Executes the approved task DAG.
     *
     * <p>After every DAG task succeeds, deterministic runtime verification is
     * performed before the run can enter AWAITING_REVIEW.
     */
    private void executePlan(String runId) {
        Run run = lifecycle.get(runId);

        List<TaskNode> planned =
                lifecycle.tasks(runId);

        if (planned.isEmpty()) {
            throw new IllegalStateException(
                    "Run " + runId + " has no plan to execute");
        }

        TaskGraph graph =
                new TaskGraph(
                        planned.stream()
                                .map(
                                        node ->
                                                new TaskSpec(
                                                        node.getTaskKey(),
                                                        node.getTitle(),
                                                        node.getAgent(),
                                                        node.getDependsOn()))
                                .toList());

        Map<String, TaskNode> byKey =
                planned.stream()
                        .collect(
                                Collectors.toMap(
                                        TaskNode::getTaskKey,
                                        node -> node));

        Set<String> done =
                planned.stream()
                        .filter(
                                node ->
                                        node.getStatus()
                                                == TaskStatus.SUCCEEDED)
                        .map(TaskNode::getTaskKey)
                        .collect(Collectors.toSet());

        TaskHandler handler =
                (task, attempt) -> {

                    Agent agent =
                            agents.get(task.agent());

                    if (agent == null) {
                        throw new NonRetryableException(
                                "No agent registered for "
                                        + task.agent());
                    }

                    TaskNode taskNode =
                            byKey.get(task.key());

                    if (taskNode == null) {
                        throw new NonRetryableException(
                                "No TaskNode found for "
                                        + task.key());
                    }

                    agent.execute(
                            new AgentContext(
                                    runId,
                                    run.getScenario(),
                                    run.getRequirement(),
                                    task.key(),
                                    task.title(),
                                    taskNode.getDescription(),
                                    attempt));
                };

        ExecutionReport report;

        try {
            report =
                    dagExecutor.execute(
                            graph,
                            handler,
                            new CheckpointListener(
                                    runId,
                                    nodes,
                                    audit),
                            done);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Execution interrupted",
                    e);
        }

        /*
         * The agent DAG must completely succeed before runtime verification.
         *
         * Runtime verification is not an LLM task and therefore does not
         * participate in DAG dependency scheduling.
         */
        if (!report.allSucceeded()) {
            lifecycle.transition(
                    runId,
                    RunStatus.FAILED,
                    describeFailure(report));
            return;
        }

        /*
         * Deterministic post-DAG validation.
         *
         * RuntimeVerificationStep:
         *   1. obtains the latest source/test artifacts
         *   2. materializes them into the isolated workspace
         *   3. starts the generated application
         *   4. executes real HTTP checks
         *   5. persists RUNTIME_VERIFICATION evidence
         */
        RuntimeVerificationResult runtimeResult =
                runtimeVerificationStep.verify(runId);

        if (runtimeResult.passed()) {

            lifecycle.transition(
                    runId,
                    RunStatus.AWAITING_REVIEW);

        } else {

            lifecycle.transition(
                    runId,
                    RunStatus.FAILED,
                    "Runtime verification failed: "
                            + runtimeResult.failureReason());
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    /**
     * Pre-plan steps get the same retry / backoff / timeout treatment as DAG tasks.
     */
    private <T> T step(
            String runId,
            String key,
            AgentType agent,
            Callable<T> work) {

        AtomicReference<T> out =
                new AtomicReference<>();

        TaskSpec spec =
                new TaskSpec(
                        key,
                        key,
                        agent,
                        Set.of());

        TaskResult result =
                taskRunner.run(
                        spec,
                        (task, attempt) ->
                                out.set(work.call()),
                        new CheckpointListener(
                                runId,
                                nodes,
                                audit));

        if (!result.status().isSuccess()) {
            throw new StepFailedException(
                    "Step '"
                            + key
                            + "' "
                            + result.status()
                            + ": "
                            + result.message());
        }

        return out.get();
    }

    private void submit(
            String runId,
            Runnable step) {

        workflowPool.execute(
                () -> {
                    try {
                        step.run();
                    } catch (Throwable t) {
                        fail(runId, t);
                    }
                });
    }

    private void fail(
            String runId,
            Throwable t) {

        log.error(
                "Run {} failed",
                runId,
                t);

        try {
            lifecycle.transition(
                    runId,
                    RunStatus.FAILED,
                    t.getClass().getSimpleName()
                            + ": "
                            + t.getMessage());

        } catch (RuntimeException e) {

            log.error(
                    "Could not mark run {} as FAILED "
                            + "(already terminal or cancelled?)",
                    runId,
                    e);
        }
    }

    private static String describeFailure(
            ExecutionReport report) {

        StringBuilder sb =
                new StringBuilder();

        for (String key : report.failedKeys()) {

            TaskResult result =
                    report.results().get(key);

            sb.append(key)
                    .append(" [")
                    .append(result.status())
                    .append("]: ")
                    .append(result.message())
                    .append("; ");
        }

        if (!report.skippedKeys().isEmpty()) {
            sb.append("skipped: ")
                    .append(report.skippedKeys());
        }

        return sb.toString();
    }
}
