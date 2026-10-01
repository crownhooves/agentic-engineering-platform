package com.example.agentic.llm;

public interface LlmClient {
    LlmResponse complete(LlmRequest request);
    String modelName();
    boolean isMock();
}