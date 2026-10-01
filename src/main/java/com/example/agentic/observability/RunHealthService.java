package com.example.agentic.observability;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.domain.TaskNode;
import com.example.agentic.domain.TaskStatus;
import com.example.agentic.domain.repo.RunRepository;
import com.example.agentic.domain.repo.TaskNodeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RunHealthService {

    private static final Duration STALE_AFTER = Duration.ofMinutes(10);

    private final RunRepository runs;
    private final TaskNodeRepository tasks;

    public RunHealthService(
            RunRepository runs,
            TaskNodeRepository tasks) {
        this.runs = runs;
        this.tasks = tasks;
    }

    public HealthSnapshot snapshot() {
        List<Run> allRuns = runs.findAll();

        Map<RunStatus, Long> byStatus = new EnumMap<>(RunStatus.class);
        for (RunStatus status : RunStatus.values()) {
            byStatus.put(status, 0L);
        }

        int stale = 0;

        for (Run run : allRuns) {
            byStatus.compute(
                    run.getStatus(),
                    (key, value) -> value + 1);

            if (isStale(run)) {
                stale++;
            }
        }

        long runningTasks = 0;
        long failedTasks = 0;

        for (Run run : allRuns) {
            for (TaskNode task : tasks.findByRunIdOrderById(run.getId())) {
                if (task.getStatus() == TaskStatus.RUNNING) {
                    runningTasks++;
                } else if (task.getStatus() == TaskStatus.FAILED
                        || task.getStatus() == TaskStatus.ESCALATED) {
                    failedTasks++;
                }
            }
        }

        boolean healthy = stale == 0;

        return new HealthSnapshot(
                healthy,
                allRuns.size(),
                byStatus,
                runningTasks,
                failedTasks,
                stale);
    }

    private boolean isStale(Run run) {
        if (!isActive(run.getStatus()) || run.getUpdatedAt() == null) {
            return false;
        }

        return run.getUpdatedAt()
                .plus(STALE_AFTER)
                .isBefore(Instant.now());
    }

    private static boolean isActive(RunStatus status) {
        return switch (status) {
            case ANALYZING,
                 AWAITING_CLARIFICATION,
                 PLANNING,
                 AWAITING_PLAN_APPROVAL,
                 EXECUTING,
                 AWAITING_REVIEW,
                 CREATED -> true;
            default -> false;
        };
    }

    public record HealthSnapshot(
            boolean healthy,
            int totalRuns,
            Map<RunStatus, Long> runsByStatus,
            long runningTasks,
            long failedTasks,
            int staleRuns) {
    }
}