package com.example.agentic.api;

import com.example.agentic.api.dto.RunResponse;
import com.example.agentic.api.dto.TaskResponse;
import com.example.agentic.domain.Run;
import com.example.agentic.domain.TaskNode;
import java.time.Instant;
import java.util.List;

public record RunEvent(
        String type,
        String runId,
        Instant timestamp,
        RunResponse run,
        List<TaskResponse> tasks) {

    public static RunEvent snapshot(
            String type,
            Run run,
            List<TaskNode> tasks) {

        return new RunEvent(
                type,
                run.getId(),
                Instant.now(),
                RunResponse.from(run),
                tasks.stream().map(TaskResponse::from).toList());
    }
}