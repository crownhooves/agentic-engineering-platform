package com.example.agentic.orchestrator;

import com.example.agentic.common.EscalationRequiredException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs ONE task with per-attempt timeout, retry with backoff and escalation.
 * Attempts run on a separate pool so a timeout can interrupt them without
 * consuming the DAG worker that is waiting.
 */
public final class TaskRunner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    @FunctionalInterface
    public interface Sleeper { void sleep(Duration d) throws InterruptedException; }

    public static class TaskTimeoutException extends RuntimeException {
        public TaskTimeoutException(String message) { super(message); }
    }

    private final RetryPolicy retryPolicy;
    private final Duration attemptTimeout;
    private final Sleeper sleeper;
    private final ExecutorService attemptPool;

    public TaskRunner(RetryPolicy retryPolicy, Duration attemptTimeout, Sleeper sleeper) {
        this.retryPolicy = retryPolicy;
        this.attemptTimeout = attemptTimeout;
        this.sleeper = sleeper;
        AtomicInteger n = new AtomicInteger();
        this.attemptPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "task-attempt-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public TaskRunner(RetryPolicy retryPolicy, Duration attemptTimeout) {
        this(retryPolicy, attemptTimeout, d -> Thread.sleep(d.toMillis()));
    }

    public TaskResult run(TaskSpec task, TaskHandler handler, TaskListener listener) {
        Instant start = Instant.now();
        int attempt = 0;
        while (true) {
            attempt++;
            final int current = attempt;
            safe(() -> listener.onStarted(task, current));
            try {
                attemptWithTimeout(task, handler, current);
                return TaskResult.succeeded(current, since(start));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return TaskResult.failed(current, "Interrupted", since(start));
            } catch (Exception e) {
                if (e instanceof EscalationRequiredException) {
                    return TaskResult.escalated(current, e.getMessage(), since(start));
                }
                if (!retryPolicy.shouldRetry(e, current)) {
                    return TaskResult.failed(current, describe(e), since(start));
                }
                Duration backoff = retryPolicy.backoffAfter(current);
                safe(() -> listener.onRetry(task, current, e, backoff));
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return TaskResult.failed(current, "Interrupted during backoff", since(start));
                }
            }
        }
    }

    private void attemptWithTimeout(TaskSpec task, TaskHandler handler, int attempt) throws Exception {
        Future<?> f = attemptPool.submit(() -> {
            handler.execute(task, attempt);
            return null;
        });
        try {
            f.get(attemptTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            f.cancel(true);
            throw new TaskTimeoutException("Task " + task.key() + " timed out after " + attemptTimeout);
        } catch (InterruptedException ie) {
            f.cancel(true);
            throw ie;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        }
    }

    private static Duration since(Instant start) { return Duration.between(start, Instant.now()); }

    private static String describe(Throwable t) {
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (RuntimeException e) { log.warn("Task listener failed", e); }
    }

    @Override
    public void close() { attemptPool.shutdownNow(); }
}