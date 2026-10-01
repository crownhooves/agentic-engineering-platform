package com.example.agentic.tools;

import com.example.agentic.domain.ArtifactType;
import com.example.agentic.orchestrator.RunContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Creates and inspects the executable artifact produced from a run workspace.
 *
 * <p>This service does not execute arbitrary commands. Maven execution is
 * delegated to {@link CompileAndTestRunner}, which owns process execution,
 * timeout handling and bounded output capture.
 *
 * <p>The executable JAR itself remains in the isolated run workspace. Only
 * reviewable metadata about the executable is persisted as an
 * {@link ArtifactType#EXECUTABLE} artifact.
 */
@Component
public final class ExecutableArtifactService {

    private static final String JAR_EXTENSION = ".jar";

    private static final String EXECUTABLE_METADATA_PATH =
            "target/executable-metadata.json";

    private final WorkspaceManager workspaceManager;
    private final CompileAndTestRunner compileAndTestRunner;
    private final RunContext context;

    /**
     * Production constructor.
     *
     * <p>Spring uses this constructor so executable metadata can be persisted
     * as an EXECUTABLE artifact.
     */
    @Autowired
    public ExecutableArtifactService(
            WorkspaceManager workspaceManager,
            CompileAndTestRunner compileAndTestRunner,
            RunContext context) {

        this.workspaceManager = workspaceManager;
        this.compileAndTestRunner = compileAndTestRunner;
        this.context = context;
    }

    /**
     * Backward-compatible constructor used by the focused runtime integration
     * test. That test exercises executable packaging and HTTP verification
     * without setting up the artifact persistence layer.
     */
    public ExecutableArtifactService(
            WorkspaceManager workspaceManager,
            CompileAndTestRunner compileAndTestRunner) {

        this(
                workspaceManager,
                compileAndTestRunner,
                null);
    }

    /**
     * Packages the generated project and returns metadata for the produced
     * executable JAR.
     *
     * <p>The executable binary is kept in the isolated run workspace. A
     * metadata artifact is persisted when a RunContext is available so that
     * the executable becomes visible as engineering evidence without storing
     * binary content in the database.
     */
    public ExecutableArtifact packageExecutable(String runId) {

        Path workspace =
                workspaceManager.workspace(runId);

        if (!Files.isDirectory(workspace)) {
            throw new IllegalStateException(
                    "Workspace does not exist for run " + runId);
        }

        CompileAndTestRunner.PackageResult result =
                compileAndTestRunner.packageExecutable(workspace);

        if (!result.success()) {
            throw new IllegalStateException(
                    "Executable packaging failed for run "
                            + runId
                            + ":\n"
                            + result.output());
        }

        Path jar =
                findExecutableJar(workspace);

        try {
            long size = Files.size(jar);

            if (size <= 0) {
                throw new IllegalStateException(
                        "Produced executable JAR is empty: " + jar);
            }

            String sha256 =
                    sha256(jar);

            ExecutableArtifact executable =
                    new ExecutableArtifact(
                            runId,
                            jar,
                            jar.getFileName().toString(),
                            size,
                            sha256,
                            result.duration());

            persistMetadata(executable);

            return executable;

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to inspect executable JAR "
                            + jar,
                    e);
        }
    }

    /**
     * Returns the executable JAR previously produced for the run.
     */
    public Path findExecutable(String runId) {

        Path workspace =
                workspaceManager.workspace(runId);

        return findExecutableJar(workspace);
    }

    /**
     * Persists reviewable metadata for the executable artifact.
     *
     * <p>The binary JAR is intentionally not stored in the artifact database.
     * The actual executable remains in the isolated workspace while this
     * artifact records its filename, size, checksum and packaging duration.
     *
     * <p>The context may be null when this service is constructed through the
     * two-argument constructor used by the focused runtime integration test.
     */
    private void persistMetadata(
            ExecutableArtifact executable) {

        if (context == null) {
            return;
        }

        ExecutableMetadata metadata =
                new ExecutableMetadata(
                        executable.fileName(),
                        executable.size(),
                        executable.sha256(),
                        executable.packagingDuration());

        context.putJson(
                executable.runId(),
                "executable-packaging",
                ArtifactType.EXECUTABLE,
                EXECUTABLE_METADATA_PATH,
                metadata);
    }

    /**
     * Finds the executable JAR produced by Maven.
     *
     * <p>The Maven Shade plugin can leave both the shaded executable and the
     * original unshaded artifact in target/. The original artifact is
     * excluded so callers receive the executable JAR.
     */
    private static Path findExecutableJar(Path workspace) {

        Path target =
                workspace
                        .resolve("target")
                        .normalize();

        Path normalizedWorkspace =
                workspace
                        .toAbsolutePath()
                        .normalize();

        if (!target.startsWith(normalizedWorkspace)) {
            throw new IllegalStateException(
                    "Target directory escapes workspace");
        }

        if (!Files.isDirectory(target)) {
            throw new IllegalStateException(
                    "Maven target directory does not exist: "
                            + target);
        }

        try (var stream = Files.list(target)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .endsWith(JAR_EXTENSION))
                    .filter(
                            path ->
                                    !path.getFileName()
                                            .toString()
                                            .startsWith("original-"))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "No executable JAR produced in "
                                                    + target));

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to inspect Maven target directory: "
                            + target,
                    e);
        }
    }

    /**
     * Calculates the SHA-256 checksum of the executable JAR.
     */
    private static String sha256(Path file) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            try (InputStream input =
                         Files.newInputStream(file)) {

                byte[] buffer =
                        new byte[8192];

                int read;

                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            return HexFormat
                    .of()
                    .formatHex(digest.digest());

        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(
                    "Unable to calculate SHA-256 for "
                            + file,
                    e);
        }
    }

    /**
     * Metadata persisted as the EXECUTABLE artifact content.
     */
    private record ExecutableMetadata(
            String fileName,
            long size,
            String sha256,
            Duration packagingDuration) {
    }

    /**
     * Runtime information about the executable artifact.
     */
    public record ExecutableArtifact(
            String runId,
            Path path,
            String fileName,
            long size,
            String sha256,
            Duration packagingDuration) {
    }
}