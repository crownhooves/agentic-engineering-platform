package com.example.agentic.api.dto;

import com.example.agentic.agents.AgentType;
import com.example.agentic.domain.TaskNode;
import com.example.agentic.domain.TaskStatus;
import java.time.Instant;
import java.util.Set;

public record TaskResponse(
        Long id,
        String taskKey,
        String title,
        String description,
        AgentType agent,
        Set<String> dependsOn,
        TaskStatus status,
        int attempts,
        String lastError,
        Instant startedAt,
        Instant finishedAt) {

    public static TaskResponse from(TaskNode task) {
        return new TaskResponse(
                task.getId(),
                task.getTaskKey(),
                task.getTitle(),
                task.getDescription(),
                task.getAgent(),
                Set.copyOf(task.getDependsOn()),
                task.getStatus(),
                task.getAttempts(),
                task.getLastError(),
                task.getStartedAt(),
                task.getFinishedAt());
    }
}