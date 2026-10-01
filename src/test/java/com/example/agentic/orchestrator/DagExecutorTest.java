package com.example.agentic.orchestrator;

import static com.example.agentic.orchestrator.TaskGraphTest.t;
import static org.assertj.core.api.Assertions.*;

import com.example.agentic.common.EscalationRequiredException;
import com.example.agentic.common.NonRetryableException;
import com.example.agentic.domain.TaskStatus;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DagExecutorTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private TaskRunner runner;

    private DagExecutor executor(int maxAttempts, Duration timeout) {
        runner = new TaskRunner(
                new RetryPolicy(maxAttempts, Duration.ofMillis(1), 2.0, Duration.ofMillis(5), 0),
                timeout, d -> {});                               // no real sleeping in tests
        return new DagExecutor(pool, runner);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        if (runner != null) runner.close();
    }

    static class Recorder implements TaskListener {
        final Queue<String> events = new ConcurrentLinkedQueue<>();
        final AtomicInteger retries = new AtomicInteger();
        @Override public void onStarted(TaskSpec t, int a) { events.add("start:" + t.key()); }
        @Override public void onRetry(TaskSpec t, int a, Throwable e, Duration b) { retries.incrementAndGet(); }
        @Override public void onFinished(TaskSpec t, TaskResult r) { events.add("end:" + t.key()); }
    }

    @Test
    void respectsDependencyOrder() throws Exception {
        TaskGraph g = new TaskGraph(List.of(t("A"), t("B", "A"), t("C", "A"), t("D", "B", "C")));
        Recorder rec = new Recorder();
        ExecutionReport report = executor(1, Duration.ofSeconds(5))
                .execute(g, (task, attempt) -> Thread.sleep(20), rec, Set.of());

        List<String> ev = new ArrayList<>(rec.events);
        assertThat(report.allSucceeded()).isTrue();
        assertThat(ev.indexOf("end:A")).isLessThan(ev.indexOf("start:B"));
        assertThat(ev.indexOf("end:A")).isLessThan(ev.indexOf("start:C"));
        assertThat(ev.indexOf("end:B")).isLessThan(ev.indexOf("start:D"));
        assertThat(ev.indexOf("end:C")).isLessThan(ev.indexOf("start:D"));
    }

    @Test
    void runsIndependentTasksInParallel() throws Exception {
        // B and C rendezvous at a barrier: this only completes if they truly run concurrently.
        CyclicBarrier barrier = new CyclicBarrier(2);
        TaskGraph g = new TaskGraph(List.of(t("A"), t("B", "A"), t("C", "A")));
        TaskHandler h = (task, attempt) -> {
            if (!task.key().equals("A")) barrier.await(5, TimeUnit.SECONDS);
        };
        ExecutionReport report = executor(1, Duration.ofSeconds(10)).execute(g, h, TaskListener.NOOP, Set.of());
        assertThat(report.allSucceeded()).isTrue();
    }

    @Test
    void failureSkipsOnlyDownstreamTasks() throws Exception {
        // A -> B -> C  and an independent D
        TaskGraph g = new TaskGraph(List.of(t("A"), t("B", "A"), t("C", "B"), t("D")));
        TaskHandler h = (task, attempt) -> {
            if (task.key().equals("B")) throw new NonRetryableException("boom");
        };
        ExecutionReport r = executor(3, Duration.ofSeconds(5)).execute(g, h, TaskListener.NOOP, Set.of());

        assertThat(r.results().get("A").status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(r.results().get("B").status()).isEqualTo(TaskStatus.FAILED);
        assertThat(r.results().get("B").attempts()).isEqualTo(1);           // not retried
        assertThat(r.results().get("C").status()).isEqualTo(TaskStatus.SKIPPED);
        assertThat(r.results().get("D").status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(r.allSucceeded()).isFalse();
        assertThat(r.failedKeys()).containsExactly("B");
        assertThat(r.skippedKeys()).containsExactly("C");
    }

    @Test
    void transientFailuresAreRetriedWithBackoff() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Recorder rec = new Recorder();
        TaskHandler h = (task, attempt) -> {
            if (calls.incrementAndGet() < 3) throw new IllegalStateException("flaky LLM");
        };
        ExecutionReport r = executor(3, Duration.ofSeconds(5))
                .execute(new TaskGraph(List.of(t("A"))), h, rec, Set.of());

        assertThat(r.results().get("A").status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(r.results().get("A").attempts()).isEqualTo(3);
        assertThat(rec.retries.get()).isEqualTo(2);
    }

    @Test
    void exhaustedRetriesFailTheTask() throws Exception {
        TaskHandler h = (task, attempt) -> { throw new IllegalStateException("always"); };
        ExecutionReport r = executor(2, Duration.ofSeconds(5))
                .execute(new TaskGraph(List.of(t("A"))), h, TaskListener.NOOP, Set.of());
        assertThat(r.results().get("A").status()).isEqualTo(TaskStatus.FAILED);
        assertThat(r.results().get("A").attempts()).isEqualTo(2);
    }

    @Test
    void escalationIsNotRetriedAndBlocksDependents() throws Exception {
        TaskGraph g = new TaskGraph(List.of(t("A"), t("B", "A")));
        TaskHandler h = (task, attempt) -> {
            if (task.key().equals("A")) throw new EscalationRequiredException("repair loop exhausted");
        };
        ExecutionReport r = executor(3, Duration.ofSeconds(5)).execute(g, h, TaskListener.NOOP, Set.of());
        assertThat(r.results().get("A").status()).isEqualTo(TaskStatus.ESCALATED);
        assertThat(r.results().get("A").attempts()).isEqualTo(1);
        assertThat(r.results().get("B").status()).isEqualTo(TaskStatus.SKIPPED);
    }

    @Test
    void timedOutAttemptsAreInterruptedAndRetried() throws Exception {
        TaskHandler h = (task, attempt) -> Thread.sleep(5_000);
        ExecutionReport r = executor(2, Duration.ofMillis(50))
                .execute(new TaskGraph(List.of(t("A"))), h, TaskListener.NOOP, Set.of());
        assertThat(r.results().get("A").status()).isEqualTo(TaskStatus.FAILED);
        assertThat(r.results().get("A").attempts()).isEqualTo(2);
        assertThat(r.results().get("A").message()).contains("timed out");
    }

    @Test
    void resumeSkipsAlreadySucceededTasks() throws Exception {
        TaskGraph g = new TaskGraph(List.of(t("A"), t("B", "A"), t("C", "B")));
        Set<String> executed = ConcurrentHashMap.newKeySet();
        TaskHandler h = (task, attempt) -> executed.add(task.key());

        ExecutionReport r = executor(1, Duration.ofSeconds(5)).execute(g, h, TaskListener.NOOP, Set.of("A"));

        assertThat(executed).containsExactlyInAnyOrder("B", "C");   // A was not re-run
        assertThat(r.allSucceeded()).isTrue();
        assertThat(r.results().get("A").attempts()).isZero();
    }
}