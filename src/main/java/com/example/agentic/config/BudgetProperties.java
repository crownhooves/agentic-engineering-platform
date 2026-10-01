package com.example.agentic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentic.budget")
public record BudgetProperties(long maxTokensPerRun, int maxLlmCallsPerRun) {
    public BudgetProperties {
        if (maxTokensPerRun <= 0) maxTokensPerRun = 500_000;
        if (maxLlmCallsPerRun <= 0) maxLlmCallsPerRun = 100;
    }
}