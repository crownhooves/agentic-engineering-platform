package com.example.agentic.api;

import com.example.agentic.domain.RunStatus;
import com.example.agentic.domain.TaskNode;
import com.example.agentic.orchestrator.RunLifecycle;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RunEventStreamService {

    private static final long POLL_INTERVAL_MS = 500;

    private final RunLifecycle lifecycle;
    private final ScheduledExecutorService scheduler;

    public RunEventStreamService(RunLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        this.scheduler = Executors.newScheduledThreadPool(
                4,
                runnable -> {
                    Thread thread = new Thread(runnable, "agentic-sse");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public SseEmitter open(String runId) {
        lifecycle.get(runId);

        SseEmitter emitter =
                new SseEmitter(Duration.ofHours(1).toMillis());

        AtomicBoolean closed = new AtomicBoolean(false);

        final String[] previousState = {null};
        final String[] previousTasks = {null};

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> poll(
                        runId,
                        emitter,
                        closed,
                        previousState,
                        previousTasks),
                0,
                POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS);

        Runnable cleanup = () -> {
            if (closed.compareAndSet(false, true)) {
                future.cancel(false);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> cleanup.run());

        return emitter;
    }

    private void poll(
            String runId,
            SseEmitter emitter,
            AtomicBoolean closed,
            String[] previousState,
            String[] previousTasks) {

        if (closed.get()) {
            return;
        }

        try {
            var run = lifecycle.get(runId);
            List<TaskNode> tasks = lifecycle.tasks(runId);

            String state = run.getStatus().name();

            String taskState = tasks.stream()
                    .map(t -> t.getTaskKey()
                            + ":" + t.getStatus()
                            + ":" + t.getAttempts())
                    .reduce("", (a, b) -> a + "|" + b);

            boolean stateChanged =
                    !state.equals(previousState[0]);

            boolean tasksChanged =
                    !taskState.equals(previousTasks[0]);

            if (stateChanged || tasksChanged) {

                String eventType = stateChanged
                        ? "run.state_changed"
                        : "task.state_changed";

                emitter.send(
                        SseEmitter.event()
                                .name(eventType)
                                .data(RunEvent.snapshot(
                                        eventType,
                                        run,
                                        tasks)));

                previousState[0] = state;
                previousTasks[0] = taskState;
            }

            if (isTerminal(run.getStatus())) {
                emitter.send(
                        SseEmitter.event()
                                .name("run.completed")
                                .data(RunEvent.snapshot(
                                        "run.completed",
                                        run,
                                        tasks)));

                if (closed.compareAndSet(false, true)) {
                    emitter.complete();
                }
            }

        } catch (IOException | RuntimeException e) {
            if (closed.compareAndSet(false, true)) {
                emitter.completeWithError(e);
            }
        }
    }

    private static boolean isTerminal(RunStatus status) {
        return switch (status) {
            case COMPLETED, REJECTED, FAILED, CANCELLED -> true;
            default -> false;
        };
    }
}