package com.example.agentic.api.dto;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.TaskNode;
import java.util.List;

public record RunStatusResponse(
        RunResponse run,
        List<TaskResponse> tasks,
        int totalTasks,
        int completedTasks,
        int failedTasks,
        int runningTasks,
        int pendingTasks) {

    public static RunStatusResponse from(Run run, List<TaskNode> tasks) {
        int completed = 0;
        int failed = 0;
        int running = 0;
        int pending = 0;

        for (TaskNode task : tasks) {
            switch (task.getStatus()) {
                case SUCCEEDED -> completed++;
                case FAILED, ESCALATED -> failed++;
                case RUNNING -> running++;
                case PENDING, SKIPPED -> pending++;
            }
        }

        return new RunStatusResponse(
                RunResponse.from(run),
                tasks.stream().map(TaskResponse::from).toList(),
                tasks.size(),
                completed,
                failed,
                running,
                pending);
    }
}