package com.example.agentic.llm;

import java.time.Duration;

public record LlmResponse(String content, TokenUsage usage, String model, Duration latency) {}