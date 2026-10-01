package com.example.agentic.domain;

public enum TaskStatus {
    PENDING, RUNNING, SUCCEEDED, FAILED, ESCALATED, SKIPPED;

    public boolean isSuccess() { return this == SUCCEEDED; }
}