package com.example.agentic.config;

import com.example.agentic.orchestrator.DagExecutor;
import com.example.agentic.orchestrator.RetryPolicy;
import com.example.agentic.orchestrator.TaskRunner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean
    RetryPolicy retryPolicy(OrchestratorProperties p) {
        return new RetryPolicy(p.maxAttempts(), p.initialBackoff(), 2.0, p.maxBackoff(), 0.2);
    }

    @Bean
    TaskRunner taskRunner(RetryPolicy retryPolicy, OrchestratorProperties p) {
        return new TaskRunner(retryPolicy, p.stepTimeout());   // AutoCloseable -> closed on shutdown
    }

    /** Runs whole workflow steps (analyze+plan, execute-plan). Separate from the DAG worker pool. */
    @Bean(destroyMethod = "shutdown")
    ExecutorService workflowPool() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "workflow-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService dagWorkerPool(OrchestratorProperties p) {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(p.maxParallelTasks(), p.maxParallelTasks(),
                60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "dag-worker-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
    }

    @Bean
    DagExecutor dagExecutor(@Qualifier("dagWorkerPool") ExecutorService pool, TaskRunner runner) {
        return new DagExecutor(pool, runner);
    }
}