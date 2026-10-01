package com.example.agentic.orchestrator;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ready-set scheduler. A task launches the moment its last dependency succeeds, so independent
 * branches run in parallel (bounded by the pool size). When a task fails or escalates, only its
 * transitive dependents are skipped; unrelated branches keep going (continue-on-error).
 * Pass already-succeeded keys to resume a previous execution.
 */
public final class DagExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagExecutor.class);

    private record Completed(String key, TaskResult result) {}

    private final ExecutorService pool;
    private final TaskRunner runner;

    public DagExecutor(ExecutorService pool, TaskRunner runner) {
        this.pool = pool;
        this.runner = runner;
    }

    public ExecutionReport execute(TaskGraph graph, TaskHandler handler, TaskListener listener,
                                   Set<String> alreadySucceeded) throws InterruptedException {
        Map<String, TaskResult> results = new LinkedHashMap<>();
        Map<String, Integer> pending = new LinkedHashMap<>();      // key -> unmet dependency count

        for (TaskSpec t : graph.topologicalOrder()) {
            if (alreadySucceeded.contains(t.key())) {
                results.put(t.key(), TaskResult.alreadyDone());
            } else {
                int unmet = (int) t.dependsOn().stream().filter(d -> !alreadySucceeded.contains(d)).count();
                pending.put(t.key(), unmet);
            }
        }

        CompletionService<Completed> completions = new ExecutorCompletionService<>(pool);
        Map<String, Future<Completed>> inFlight = new HashMap<>();

        Consumer<String> launch = key -> {
            pending.remove(key);
            TaskSpec spec = graph.get(key);
            inFlight.put(key, completions.submit(() -> {
                TaskResult r;
                try {
                    r = runner.run(spec, handler, listener);
                } catch (Throwable t) {
                    r = TaskResult.failed(0, "Unexpected: " + t, Duration.ZERO);
                }
                final TaskResult finalResult = r;               // effectively final copy for the lambda
                safe(() -> listener.onFinished(spec, finalResult));
                return new Completed(key, finalResult);
            }));
        };

        for (String key : new ArrayList<>(pending.keySet())) {
            if (pending.get(key) == 0) launch.accept(key);
        }

        try {
            while (!inFlight.isEmpty()) {
                Completed c = completions.take().get();
                inFlight.remove(c.key());
                results.put(c.key(), c.result());

                if (c.result().status().isSuccess()) {
                    for (String dep : graph.dependentsOf(c.key())) {
                        Integer left = pending.get(dep);
                        if (left == null) continue;                 // skipped, running or done
                        if (left - 1 == 0) launch.accept(dep); else pending.put(dep, left - 1);
                    }
                } else {
                    for (String d : graph.transitiveDependents(c.key())) {
                        if (results.containsKey(d) || inFlight.containsKey(d)) continue;
                        pending.remove(d);
                        String reason = "Upstream task " + c.key() + " " + c.result().status();
                        results.put(d, TaskResult.skipped(reason));
                        safe(() -> listener.onSkipped(graph.get(d), reason));
                    }
                }
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException("Scheduler failure", e);
        } finally {
            inFlight.values().forEach(f -> f.cancel(true));       // only non-empty on interrupt
        }

        // Safety net: anything never scheduled (should not happen for a valid graph).
        for (String key : pending.keySet()) {
            results.put(key, TaskResult.skipped("Never became ready"));
        }
        return new ExecutionReport(results);
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (RuntimeException e) { log.warn("Listener failed", e); }
    }
}