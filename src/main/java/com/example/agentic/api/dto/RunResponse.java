package com.example.agentic.api.dto;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import java.time.Instant;

public record RunResponse(
        String id,
        String requirement,
        String scenario,
        RunStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static RunResponse from(Run run) {
        return new RunResponse(
                run.getId(),
                run.getRequirement(),
                run.getScenario(),
                run.getStatus(),
                run.getFailureReason(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }
}