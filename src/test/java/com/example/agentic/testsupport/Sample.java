package com.example.agentic.testsupport;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.llm.Validatable;

public record Sample(String name, int count) implements Validatable {
    @Override
    public void validate() {
        if (name == null || name.isBlank()) throw new InvalidOutputException("field 'name' is required");
    }
}