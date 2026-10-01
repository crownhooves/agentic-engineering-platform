package com.example.agentic.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentic.llm")
public record LlmProperties(Duration mockLatency, Duration callTimeout, Integer maxOutputRepairs) {
    public LlmProperties {
        if (mockLatency == null) mockLatency = Duration.ofMillis(150);
        if (callTimeout == null) callTimeout = Duration.ofSeconds(120);
        if (maxOutputRepairs == null || maxOutputRepairs < 0) maxOutputRepairs = 2;
    }
}