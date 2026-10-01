
package com.example.agentic.api;

import com.example.agentic.orchestrator.RunLifecycle;
import com.example.agentic.tools.ExecutableArtifactService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides controlled access to the executable artifact generated for a run.
 *
 * <p>The executable JAR is kept in the isolated run workspace and is not
 * stored as binary content in the Artifact entity. The path is resolved
 * exclusively through ExecutableArtifactService.
 */
@RestController
@RequestMapping("/api/runs/{runId}/executable")
public class ExecutableController {

    private final RunLifecycle lifecycle;
    private final ExecutableArtifactService executableArtifactService;

    public ExecutableController(
            RunLifecycle lifecycle,
            ExecutableArtifactService executableArtifactService) {

        this.lifecycle = lifecycle;
        this.executableArtifactService = executableArtifactService;
    }

    /**
     * Downloads the executable JAR generated for the specified run.
     *
     * @param runId run identifier
     * @return executable JAR as an attachment
     */
    @GetMapping
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String runId) {

        // Verify that the requested run exists.
        lifecycle.get(runId);

        Path executable =
                executableArtifactService
                        .findExecutable(runId)
                        .toAbsolutePath()
                        .normalize();

        if (!Files.isRegularFile(executable)) {
            throw new NoSuchElementException(
                    "Executable JAR not found for run: " + runId);
        }

        try {
            String fileName =
                    executable.getFileName().toString();

            InputStreamResource resource =
                    new InputStreamResource(
                            Files.newInputStream(executable));

            return ResponseEntity.ok()
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/java-archive"))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .contentLength(Files.size(executable))
                    .body(resource);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to read executable JAR for run "
                            + runId,
                    e);
        }
    }
}

