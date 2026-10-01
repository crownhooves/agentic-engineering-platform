package com.example.agentic.orchestrator;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.domain.repo.ArtifactRepository;
import com.example.agentic.guardrails.GuardrailViolationException;
import com.example.agentic.guardrails.PathGuard;
import com.example.agentic.llm.StructuredOutputParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Shared blackboard: agents never call each other, they read and write revisioned artifacts here.
 * Every path is validated against a virtual root so nothing unsafe is ever stored, let alone written to disk.
 */
@Component
public class RunContext {

    private static final PathGuard VIRTUAL_ROOT = new PathGuard(Path.of("/agentic-virtual-root"));

    private final ArtifactRepository artifacts;
    private final StructuredOutputParser parser;

    public RunContext(ArtifactRepository artifacts, StructuredOutputParser parser) {
        this.artifacts = artifacts;
        this.parser = parser;
    }

    /** Reject-able by the re-prompt loop: a bad path from the model is an output error, not a crash. */
    public static void requireSafePath(String path) {
        try {
            VIRTUAL_ROOT.resolve(path);
        } catch (GuardrailViolationException e) {
            throw new InvalidOutputException("Illegal artifact path '" + path + "': " + e.getMessage(), e);
        }
    }

    public Artifact put(String runId, String taskKey, ArtifactType type, String path, String content) {
        requireSafePath(path);
        int revision = artifacts.findByRunIdAndPathOrderByRevisionDesc(runId, path).stream()
                .findFirst().map(a -> a.getRevision() + 1).orElse(1);
        return artifacts.save(new Artifact(runId, taskKey, type, path, content, revision));
    }

    public Artifact putJson(String runId, String taskKey, ArtifactType type, String path, Object value) {
        return put(runId, taskKey, type, path, parser.toJson(value));
    }

    public Optional<Artifact> latest(String runId, ArtifactType type) {
        return artifacts.findByRunIdAndTypeOrderByIdDesc(runId, type).stream().findFirst();
    }

    public <T> Optional<T> latestJson(String runId, ArtifactType type, Class<T> cls) {
        return latest(runId, type).map(a -> parser.parse(a.getContent(), cls));
    }

    /** Highest revision of every path, restricted to the given types, ordered by path. */
    public List<Artifact> currentFiles(String runId, ArtifactType... types) {
        List<ArtifactType> wanted = List.of(types);
        Map<String, Artifact> latest = new TreeMap<>();
        for (Artifact a : artifacts.findByRunIdOrderByCreatedAtAsc(runId)) {
            if (wanted.contains(a.getType())) {
                latest.merge(a.getPath(), a, (x, y) -> y.getRevision() >= x.getRevision() ? y : x);
            }
        }
        return List.copyOf(latest.values());
    }

    /** Markdown list of what is really stored; used by the summary so artifacts cannot be hallucinated. */
    public String index(String runId) {
        StringBuilder sb = new StringBuilder();
        for (Artifact a : currentFiles(runId, ArtifactType.values())) {
            sb.append("- `").append(a.getPath()).append("` (").append(a.getType())
                    .append(", revision ").append(a.getRevision()).append(")\n");
        }
        return sb.toString();
    }
}