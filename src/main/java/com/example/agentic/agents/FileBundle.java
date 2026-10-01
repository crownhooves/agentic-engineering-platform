package com.example.agentic.agents;

import java.util.List;

public record FileBundle(List<GeneratedFile> files) {
    public record GeneratedFile(String path, String content) {}
}