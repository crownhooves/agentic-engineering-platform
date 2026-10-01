package com.example.agentic.tools;

import java.time.Duration;
import java.util.List;

public record RuntimeVerificationResult(
        boolean passed,
        int port,
        Duration duration,
        List<CheckResult> checks,
        String failureReason) {

    public record CheckResult(
            String name,
            boolean passed,
            int expectedStatus,
            int actualStatus,
            String detail) {
    }

    public static RuntimeVerificationResult passed(
            int port,
            Duration duration,
            List<CheckResult> checks) {

        return new RuntimeVerificationResult(
                true,
                port,
                duration,
                List.copyOf(checks),
                null);
    }

    public static RuntimeVerificationResult failed(
            int port,
            Duration duration,
            List<CheckResult> checks,
            String failureReason) {

        return new RuntimeVerificationResult(
                false,
                port,
                duration,
                List.copyOf(checks),
                failureReason);
    }
}