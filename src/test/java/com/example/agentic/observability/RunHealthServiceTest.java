package com.example.agentic.observability;

import com.example.agentic.agents.AgentType;
import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.domain.TaskNode;
import com.example.agentic.domain.TaskStatus;
import com.example.agentic.domain.repo.RunRepository;
import com.example.agentic.domain.repo.TaskNodeRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RunHealthServiceTest {

    @Test
    void reportsHealthyWhenNoActiveRunIsStale() throws Exception {
        RunRepository runs =
                mock(RunRepository.class);

        TaskNodeRepository tasks =
                mock(TaskNodeRepository.class);

        Run run = run(
                RunStatus.EXECUTING,
                Instant.now()
        );

        given(runs.findAll())
                .willReturn(List.of(run));

        given(tasks.findByRunIdOrderById(run.getId()))
                .willReturn(List.of());

        RunHealthService service =
                new RunHealthService(runs, tasks);

        RunHealthService.HealthSnapshot snapshot =
                service.snapshot();

        assertThat(snapshot.totalRuns())
                .isEqualTo(1);

        assertThat(snapshot.staleRuns())
                .isZero();

        assertThat(snapshot.healthy())
                .isTrue();

        assertThat(snapshot.runsByStatus()
                .get(RunStatus.EXECUTING))
                .isEqualTo(1L);

        assertThat(snapshot.runningTasks())
                .isZero();

        assertThat(snapshot.failedTasks())
                .isZero();
    }

    @Test
    void reportsUnhealthyWhenActiveRunIsStale() throws Exception {
        RunRepository runs =
                mock(RunRepository.class);

        TaskNodeRepository tasks =
                mock(TaskNodeRepository.class);

        Instant old = Instant.now()
                .minusSeconds(11 * 60);

        Run run = run(
                RunStatus.EXECUTING,
                old
        );

        given(runs.findAll())
                .willReturn(List.of(run));

        given(tasks.findByRunIdOrderById(run.getId()))
                .willReturn(List.of());

        RunHealthService service =
                new RunHealthService(runs, tasks);

        RunHealthService.HealthSnapshot snapshot =
                service.snapshot();

        assertThat(snapshot.totalRuns())
                .isEqualTo(1);

        assertThat(snapshot.staleRuns())
                .isEqualTo(1);

        assertThat(snapshot.healthy())
                .isFalse();

        assertThat(snapshot.runsByStatus()
                .get(RunStatus.EXECUTING))
                .isEqualTo(1L);
    }

    @Test
    void terminalRunsAreNotStale() throws Exception {
        RunRepository runs =
                mock(RunRepository.class);

        TaskNodeRepository tasks =
                mock(TaskNodeRepository.class);

        Instant old = Instant.now()
                .minusSeconds(60 * 60);

        Run run = run(
                RunStatus.COMPLETED,
                old
        );

        given(runs.findAll())
                .willReturn(List.of(run));

        given(tasks.findByRunIdOrderById(run.getId()))
                .willReturn(List.of());

        RunHealthService service =
                new RunHealthService(runs, tasks);

        RunHealthService.HealthSnapshot snapshot =
                service.snapshot();

        assertThat(snapshot.totalRuns())
                .isEqualTo(1);

        assertThat(snapshot.staleRuns())
                .isZero();

        assertThat(snapshot.healthy())
                .isTrue();

        assertThat(snapshot.runsByStatus()
                .get(RunStatus.COMPLETED))
                .isEqualTo(1L);
    }

    @Test
    void countsEveryRunStatus() throws Exception {
        RunRepository runs =
                mock(RunRepository.class);

        TaskNodeRepository tasks =
                mock(TaskNodeRepository.class);

        Instant now = Instant.now();

        Run completed = run(
                RunStatus.COMPLETED,
                now
        );

        Run executing = run(
                RunStatus.EXECUTING,
                now
        );

        Run failed = run(
                RunStatus.FAILED,
                now
        );

        given(runs.findAll())
                .willReturn(List.of(
                        completed,
                        executing,
                        failed
                ));

        given(tasks.findByRunIdOrderById(completed.getId()))
                .willReturn(List.of());

        given(tasks.findByRunIdOrderById(executing.getId()))
                .willReturn(List.of());

        given(tasks.findByRunIdOrderById(failed.getId()))
                .willReturn(List.of());

        RunHealthService service =
                new RunHealthService(runs, tasks);

        RunHealthService.HealthSnapshot snapshot =
                service.snapshot();

        assertThat(snapshot.totalRuns())
                .isEqualTo(3);

        assertThat(snapshot.runsByStatus()
                .get(RunStatus.COMPLETED))
                .isEqualTo(1L);

        assertThat(snapshot.runsByStatus()
                .get(RunStatus.EXECUTING))
                .isEqualTo(1L);

        assertThat(snapshot.runsByStatus()
                .get(RunStatus.FAILED))
                .isEqualTo(1L);
    }

    @Test
    void countsRunningTasks() throws Exception {
        RunRepository runs =
                mock(RunRepository.class);

        TaskNodeRepository tasks =
                mock(TaskNodeRepository.class);

        Run run = run(
                RunStatus.EXECUTING,
                Instant.now()
        );

        TaskNode runningTask =
                task(TaskStatus.RUNNING);

        TaskNode pendingTask =
                task(TaskStatus.PENDING);

        given(runs.findAll())
                .willReturn(List.of(run));

        given(tasks.findByRunIdOrderById(run.getId()))
                .willReturn(List.of(
                        runningTask,
                        pendingTask
                ));

        RunHealthService service =
                new RunHealthService(runs, tasks);

        RunHealthService.HealthSnapshot snapshot =
                service.snapshot();

        assertThat(snapshot.runningTasks())
                .isEqualTo(1);

        assertThat(snapshot.failedTasks())
                .isZero();
    }

    @Test
    void countsFailedAndEscalatedTasks() throws Exception {
        RunRepository runs =
                mock(RunRepository.class);

        TaskNodeRepository tasks =
                mock(TaskNodeRepository.class);

        Run run = run(
                RunStatus.EXECUTING,
                Instant.now()
        );

        TaskNode failedTask =
                task(TaskStatus.FAILED);

        TaskNode escalatedTask =
                task(TaskStatus.ESCALATED);

        given(runs.findAll())
                .willReturn(List.of(run));

        given(tasks.findByRunIdOrderById(run.getId()))
                .willReturn(List.of(
                        failedTask,
                        escalatedTask
                ));

        RunHealthService service =
                new RunHealthService(runs, tasks);

        RunHealthService.HealthSnapshot snapshot =
                service.snapshot();

        assertThat(snapshot.runningTasks())
                .isZero();

        assertThat(snapshot.failedTasks())
                .isEqualTo(2);
    }

    private Run run(
            RunStatus status,
            Instant updatedAt
    ) throws Exception {

        Run run = new Run(
                "Test requirement",
                "test"
        );

        run.setStatus(status);

        setField(
                run,
                "createdAt",
                updatedAt
        );

        setField(
                run,
                "updatedAt",
                updatedAt
        );

        return run;
    }

    private TaskNode task(TaskStatus status) {
        TaskNode task = new TaskNode(
                "run-1",
                "task-" + status.name().toLowerCase(),
                "Test task",
                "Test task description",
                AgentType.CODER,
                Set.of()
        );

        task.setStatus(status);

        return task;
    }

    private void setField(
            Run run,
            String fieldName,
            Object value
    ) throws Exception {

        Field field =
                Run.class.getDeclaredField(fieldName);

        field.setAccessible(true);
        field.set(run, value);
    }
}