package com.example.agentic.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem and process-execution limits for the controlled engineering workspace.
 *
 * <p>The workspace is isolated per run. The reference root is used to seed a deterministic
 * Maven project for generated greenfield code and can be pointed at an existing project
 * for brownfield indexing.</p>
 */
@ConfigurationProperties(prefix = "agentic.workspace")
public record WorkspaceProperties(
        Path root,
        Path referenceRoot,
        Path brownfieldRoot,
        Duration commandTimeout,
        int maxOutputChars,
        int maxFiles) {

    public WorkspaceProperties {
        if (root == null) root = Path.of("./workspaces");
        if (referenceRoot == null) referenceRoot = Path.of("./reference/sample");
        if (brownfieldRoot == null) brownfieldRoot = referenceRoot;
        if (commandTimeout == null) commandTimeout = Duration.ofMinutes(3);
        if (commandTimeout.isZero() || commandTimeout.isNegative()) {
            commandTimeout = Duration.ofMinutes(3);
        }
        if (maxOutputChars <= 0) maxOutputChars = 50_000;
        if (maxFiles <= 0) maxFiles = 2_000;
    }
}
