package com.example.agentic.llm;

/** Implemented by agent output DTOs. Throw InvalidOutputException with a message the model can act on. */
public interface Validatable {
    void validate();
}