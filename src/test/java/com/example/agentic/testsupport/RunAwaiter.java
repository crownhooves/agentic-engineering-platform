package com.example.agentic.testsupport;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.domain.repo.RunRepository;
import com.example.agentic.orchestrator.RunStateMachine;
import java.time.Duration;

public final class RunAwaiter {

    private RunAwaiter() {}

    /** Polls until the run reaches the expected status; fails fast (with the reason) on an unexpected FAILED. */
    public static Run await(RunRepository runs, String runId, RunStatus expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Run last = runs.findById(runId).orElseThrow();
        while (System.nanoTime() < deadline) {
            last = runs.findById(runId).orElseThrow();
            if (last.getStatus() == expected) return last;
            boolean stuck = RunStateMachine.isTerminal(last.getStatus())
                    || (last.getStatus() == RunStatus.FAILED && expected != RunStatus.FAILED);
            if (stuck) break;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Run " + runId + ": expected " + expected + " but was " + last.getStatus()
                + " (reason: " + last.getFailureReason() + ")");
    }
}