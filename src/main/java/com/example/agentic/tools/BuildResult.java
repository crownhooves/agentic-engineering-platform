package com.example.agentic.tools;

import java.time.Duration;

/** Immutable result of a controlled compile/test invocation. */
public record BuildResult(
        boolean success,
        boolean compileSucceeded,
        boolean testsExecuted,
        int exitCode,
        int testsRun,
        int failures,
        String output,
        Duration duration) {

    public boolean needsRepair() {
        return !success;
    }
}
