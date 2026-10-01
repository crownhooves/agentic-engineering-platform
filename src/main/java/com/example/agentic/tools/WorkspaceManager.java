package com.example.agentic.tools;

import com.example.agentic.agents.FileBundle.GeneratedFile;
import com.example.agentic.config.WorkspaceProperties;
import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.guardrails.GuardrailViolationException;
import com.example.agentic.orchestrator.RunContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Creates an isolated filesystem workspace for one run and materializes only validated artifacts.
 *
 * <p>No model-controlled path is ever allowed to escape the workspace. Existing files are
 * replaced only when their path is present in the current artifact set.</p>
 */
@Component
public final class WorkspaceManager {

    private final WorkspaceProperties properties;

    public WorkspaceManager(WorkspaceProperties properties) {
        this.properties = properties;
    }

    /** Creates or resets a run workspace and seeds it from the configured reference project. */
    public Path prepare(String runId) {
        validateRunId(runId);
        Path workspace = workspace(runId);
        try {
            if (Files.exists(workspace)) {
                deleteTree(workspace);
            }
            Files.createDirectories(workspace);
            copyTree(properties.referenceRoot(), workspace);
            return workspace;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare workspace " + workspace, e);
        }
    }

    /**
     * Materializes generated source and test artifacts into the run workspace.
     *
     * <p>The workspace is prepared first if it does not exist. Only SOURCE_CODE and TEST_CODE
     * artifacts are written by this method.</p>
     */
    public Path materialize(String runId, List<Artifact> artifacts) {
        validateRunId(runId);
        Path workspace = Files.exists(workspace(runId)) ? workspace(runId) : prepare(runId);

        if (artifacts.size() > properties.maxFiles()) {
            throw new GuardrailViolationException(
                    "Artifact count " + artifacts.size() + " exceeds workspace limit " + properties.maxFiles());
        }

        for (Artifact artifact : artifacts) {
            if (artifact.getPath() == null || artifact.getPath().isBlank()) {
                throw new GuardrailViolationException("Artifact path must not be blank");
            }
            if (artifact.getType() != ArtifactType.SOURCE_CODE
                    && artifact.getType() != ArtifactType.TEST_CODE) {
                continue;
            }
            writeSafe(workspace, artifact.getPath(), artifact.getContent());
        }
        return workspace;
    }

    /** Materializes parser-generated files without requiring persistence first. */
    public Path materializeFiles(String runId, List<GeneratedFile> files) {
        validateRunId(runId);
        Path workspace = Files.exists(workspace(runId)) ? workspace(runId) : prepare(runId);
        if (files.size() > properties.maxFiles()) {
            throw new GuardrailViolationException("Too many generated files: " + files.size());
        }
        for (GeneratedFile file : files) {
            writeSafe(workspace, file.path(), file.content());
        }
        return workspace;
    }

    public Path workspace(String runId) {
        validateRunId(runId);
        Path root = properties.root().toAbsolutePath().normalize();
        Path result = root.resolve(runId).normalize();
        if (!result.startsWith(root)) {
            throw new GuardrailViolationException("Run id escapes workspace root");
        }
        return result;
    }

    public Path referenceRoot() {
        return properties.referenceRoot().toAbsolutePath().normalize();
    }

    public Path brownfieldRoot() {
        return properties.brownfieldRoot().toAbsolutePath().normalize();
    }

    /** Deletes the isolated run workspace. */
    public void cleanup(String runId) {
        Path workspace = workspace(runId);
        if (!Files.exists(workspace)) return;
        try {
            Files.walk(workspace)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Cannot delete " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot clean workspace " + workspace, e);
        }
    }

    private static void writeSafe(Path workspace, String relativePath, String content) {
        RunContext.requireSafePath(relativePath);
        Path target = workspace.resolve(relativePath).normalize();
        if (!target.startsWith(workspace.toAbsolutePath().normalize())) {
            throw new GuardrailViolationException("Artifact path escapes workspace: " + relativePath);
        }
        try {
            Files.createDirectories(Objects.requireNonNull(target.getParent()));
            Files.writeString(target, content == null ? "" : content,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write artifact " + relativePath, e);
        }
    }


    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot delete " + path, e);
                }
            });
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new IllegalStateException("Reference project does not exist: " + source.toAbsolutePath());
        }
        Path normalizedSource = source.toAbsolutePath().normalize();
        Files.walk(normalizedSource).forEach(path -> {
            try {
                Path relative = normalizedSource.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target.toAbsolutePath().normalize())) {
                    throw new GuardrailViolationException("Reference path escapes workspace: " + relative);
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot copy reference file " + path, e);
            }
        });
    }

    private static void validateRunId(String runId) {
        if (runId == null || runId.isBlank()
                || !runId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new GuardrailViolationException("Invalid run id");
        }
    }
}
