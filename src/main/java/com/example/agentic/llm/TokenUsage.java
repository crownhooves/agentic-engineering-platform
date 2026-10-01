package com.example.agentic.llm;

public record TokenUsage(int promptTokens, int completionTokens) {
    public static final TokenUsage ZERO = new TokenUsage(0, 0);
    public int total() { return promptTokens + completionTokens; }
    public TokenUsage plus(TokenUsage o) {
        return new TokenUsage(promptTokens + o.promptTokens, completionTokens + o.completionTokens);
    }
}