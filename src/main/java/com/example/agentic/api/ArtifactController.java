package com.example.agentic.api;

import com.example.agentic.api.dto.ArtifactResponse;
import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.repo.ArtifactRepository;
import com.example.agentic.orchestrator.RunLifecycle;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/runs/{runId}/artifacts")
public class ArtifactController {

    private final ArtifactRepository artifacts;
    private final RunLifecycle lifecycle;

    public ArtifactController(
            ArtifactRepository artifacts,
            RunLifecycle lifecycle) {
        this.artifacts = artifacts;
        this.lifecycle = lifecycle;
    }

    /**
     * Lists all artifacts produced during a run.
     *
     * Content is intentionally omitted so the response remains lightweight.
     * Revision history is preserved because it provides useful engineering
     * evidence, especially for validation/repair loops.
     */
    @GetMapping
    public List<ArtifactResponse> list(
            @PathVariable String runId) {

        // Verify that the run exists.
        lifecycle.get(runId);

        return artifacts
                .findByRunIdOrderByCreatedAtAsc(runId)
                .stream()
                .map(ArtifactResponse::metadata)
                .toList();
    }

    /**
     * Returns the complete content of a single artifact.
     *
     * The artifact must belong to the requested run. This prevents an
     * artifact from another run being accessed by manipulating the URL.
     */
    @GetMapping("/{artifactId}")
    public ArtifactResponse get(
            @PathVariable String runId,
            @PathVariable Long artifactId) {

        // Verify that the run exists first.
        lifecycle.get(runId);

        Artifact artifact = artifacts.findById(artifactId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Artifact not found: " + artifactId));

        if (!runId.equals(artifact.getRunId())) {
            throw new NoSuchElementException(
                    "Artifact not found: " + artifactId);
        }

        return ArtifactResponse.from(artifact);
    }
}
