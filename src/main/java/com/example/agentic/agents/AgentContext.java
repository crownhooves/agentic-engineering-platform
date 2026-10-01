package com.example.agentic.agents;

public record AgentContext(String runId, String scenario, String requirement, String taskKey,
                           String taskTitle, String taskDescription, int attempt) {}