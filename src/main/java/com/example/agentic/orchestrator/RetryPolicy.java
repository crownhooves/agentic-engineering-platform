package com.example.agentic.orchestrator;

import com.example.agentic.common.NonRetryableException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/** Exponential backoff with optional +/- jitter. Non-retryable exceptions are never retried. */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier,
                          Duration maxBackoff, double jitterRatio) {

    public RetryPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (initialBackoff == null || maxBackoff == null) throw new IllegalArgumentException("backoffs required");
        if (multiplier < 1.0) throw new IllegalArgumentException("multiplier must be >= 1");
        if (jitterRatio < 0 || jitterRatio > 1) throw new IllegalArgumentException("jitterRatio in [0,1]");
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(500), 2.0, Duration.ofSeconds(10), 0.2);
    }

    public boolean shouldRetry(Throwable error, int attemptJustFailed) {
        return attemptJustFailed < maxAttempts && !(error instanceof NonRetryableException);
    }

    public Duration backoffAfter(int failedAttempt) {
        double ms = initialBackoff.toMillis() * Math.pow(multiplier, Math.max(0, failedAttempt - 1));
        ms = Math.min(ms, maxBackoff.toMillis());
        if (jitterRatio > 0) {
            ms *= 1 + jitterRatio * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
        }
        return Duration.ofMillis(Math.max(0, (long) ms));
    }
}