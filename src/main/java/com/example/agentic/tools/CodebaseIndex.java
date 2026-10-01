package com.example.agentic.tools;

import java.nio.file.Path;
import java.util.List;

/** Compact deterministic description of a source tree used by the brownfield analyst. */
public record CodebaseIndex(
        Path root,
        int fileCount,
        List<String> packages,
        List<String> types,
        List<String> endpoints,
        List<String> persistenceHints,
        List<String> importantFiles) {

    public CodebaseIndex {
        packages = List.copyOf(packages);
        types = List.copyOf(types);
        endpoints = List.copyOf(endpoints);
        persistenceHints = List.copyOf(persistenceHints);
        importantFiles = List.copyOf(importantFiles);
    }

    public String toMarkdown() {
        StringBuilder out = new StringBuilder("# Codebase index\n\n")
                .append("- Root: `").append(root).append("`\n")
                .append("- Source/config files: ").append(fileCount).append("\n\n")
                .append("## Packages\n");
        packages.forEach(p -> out.append("- `").append(p).append("`\n"));
        out.append("\n## Types\n");
        types.forEach(t -> out.append("- ").append(t).append("\n"));
        out.append("\n## Endpoints\n");
        endpoints.forEach(e -> out.append("- ").append(e).append("\n"));
        out.append("\n## Persistence hints\n");
        persistenceHints.forEach(p -> out.append("- ").append(p).append("\n"));
        out.append("\n## Important files\n");
        importantFiles.forEach(f -> out.append("- `").append(f).append("`\n"));
        return out.toString();
    }
}
