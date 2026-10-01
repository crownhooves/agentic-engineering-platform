package com.example.agentic.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentic.orchestrator")
public record OrchestratorProperties(int maxParallelTasks, int maxAttempts,
                                     Duration initialBackoff, Duration maxBackoff,
                                     Duration stepTimeout) {
    public OrchestratorProperties {
        if (maxParallelTasks <= 0) maxParallelTasks = 4;
        if (maxAttempts <= 0) maxAttempts = 3;
        if (initialBackoff == null) initialBackoff = Duration.ofMillis(500);
        if (maxBackoff == null) maxBackoff = Duration.ofSeconds(10);
        if (stepTimeout == null) stepTimeout = Duration.ofSeconds(180);
    }
}