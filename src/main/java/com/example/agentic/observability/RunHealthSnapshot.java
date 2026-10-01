package com.example.agentic.observability;

import com.example.agentic.domain.RunStatus;

import java.time.Instant;
import java.util.Map;

public record RunHealthSnapshot(
        Instant timestamp,
        long totalRuns,
        long activeRuns,
        long staleRuns,
        boolean healthy,
        Map<RunStatus, Long> counts
) {
}