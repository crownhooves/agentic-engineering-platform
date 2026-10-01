package com.example.agentic.orchestrator;

import com.example.agentic.agents.AgentType;
import java.util.Set;

public record TaskSpec(String key, String title, AgentType agent, Set<String> dependsOn) {
    public TaskSpec {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Task key must not be blank");
        if (title == null) title = key;
        dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
    }
}