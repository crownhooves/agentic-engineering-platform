package com.example.agentic.llm;

import com.example.agentic.agents.AgentType;

/**
 * @param scenario  recording set for the mock client (url-shortener | brownfield | ambiguous)
 * @param callKey   which recording within the scenario, e.g. "coder" or "coder-repair-1"
 * @param jsonMode  ask the model for strict JSON output
 */
public record LlmRequest(String runId, String scenario, AgentType agent, String callKey,
                         String systemPrompt, String userPrompt, boolean jsonMode) {}