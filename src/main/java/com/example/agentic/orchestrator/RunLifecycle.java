package com.example.agentic.orchestrator;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.domain.TaskNode;
import com.example.agentic.domain.TaskStatus;
import com.example.agentic.domain.repo.RunRepository;
import com.example.agentic.domain.repo.TaskNodeRepository;
import com.example.agentic.guardrails.AuditLogger;
import com.example.agentic.guardrails.BudgetGuard;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place where run state changes. Every transition goes through RunStateMachine; concurrent
 * writers are stopped by the @Version column. Audit writes happen outside transactions on purpose.
 */
@Service
public class RunLifecycle {

    private final RunRepository runs;
    private final TaskNodeRepository tasks;
    private final AuditLogger audit;
    private final BudgetGuard budget;

    public RunLifecycle(RunRepository runs, TaskNodeRepository tasks, AuditLogger audit, BudgetGuard budget) {
        this.runs = runs;
        this.tasks = tasks;
        this.audit = audit;
        this.budget = budget;
    }

    public Run create(String requirement, String scenario) {
        Run run = runs.save(new Run(requirement, scenario));
        audit.log(run.getId(), "system", "RUN_CREATED", "scenario=" + scenario);
        return run;
    }

    public Run get(String runId) {
        return runs.findById(runId).orElseThrow(() -> new NoSuchElementException("Run not found: " + runId));
    }

    public List<Run> getAll() {
        return runs.findAllByOrderByCreatedAtDesc();
    }

    public Run transition(String runId, RunStatus to) { return transition(runId, to, null); }

    public Run transition(String runId, RunStatus to, String failureReason) {
        Run run = get(runId);
        RunStatus from = run.getStatus();
        RunStateMachine.require(from, to);
        run.setStatus(to);
        run.setFailureReason(to == RunStatus.FAILED ? failureReason : null);
        Run saved = runs.save(run);
        audit.log(runId, "system", "RUN_STATE",
                from + " -> " + to + (failureReason == null ? "" : " : " + failureReason));
        if (RunStateMachine.isTerminal(to)) budget.release(runId);
        return saved;
    }

    public List<TaskNode> tasks(String runId) { return tasks.findByRunIdOrderById(runId); }

    @Transactional
    public void replacePlan(String runId, List<TaskNode> nodes) {
        tasks.deleteByRunId(runId);
        tasks.flush();                       // Hibernate runs inserts before deletes: flush to avoid unique-key clashes
        tasks.saveAll(nodes);
    }

    /** Human plan edit: removed tasks disappear and other tasks silently drop dependencies on them. */
    @Transactional
    public void removeTasks(String runId, Set<String> keys) {
        List<TaskNode> nodes = tasks.findByRunIdOrderById(runId);
        Set<String> existing = nodes.stream().map(TaskNode::getTaskKey).collect(Collectors.toSet());
        if (!existing.containsAll(keys)) {
            throw new IllegalArgumentException("Unknown task keys: "
                    + keys.stream().filter(k -> !existing.contains(k)).sorted().toList());
        }
        if (keys.size() >= nodes.size()) throw new IllegalArgumentException("A plan must keep at least one task");
        for (TaskNode n : nodes) {
            if (keys.contains(n.getTaskKey())) tasks.delete(n);
            else if (n.getDependsOn().removeAll(keys)) tasks.save(n);
        }
    }

    /** Everything that did not succeed goes back to PENDING; succeeded work is kept (checkpoint resume). */
    @Transactional
    public int resetForResume(String runId) {
        int reset = 0;
        for (TaskNode t : tasks.findByRunIdOrderById(runId)) {
            if (t.getStatus() != TaskStatus.SUCCEEDED) {
                t.setStatus(TaskStatus.PENDING);
                t.setAttempts(0);
                t.setLastError(null);
                t.setStartedAt(null);
                t.setFinishedAt(null);
                reset++;
            }
        }
        return reset;
    }
}