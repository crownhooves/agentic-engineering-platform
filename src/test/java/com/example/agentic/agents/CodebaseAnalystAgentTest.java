package com.example.agentic.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentic.config.WorkspaceProperties;
import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.orchestrator.RunContext;
import com.example.agentic.tools.CodebaseIndexer;
import com.example.agentic.tools.WorkspaceManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodebaseAnalystAgentTest {

    @Test
    void producesImpactArtifactsFromConfiguredBrownfieldRoot() throws Exception {
        Path root = Files.createTempDirectory("brownfield");
        Files.createDirectories(root.resolve("src/main/java/demo"));
        Files.writeString(root.resolve("src/main/java/demo/App.java"),
                "package demo; public class App {}");

        WorkspaceProperties props = new WorkspaceProperties(root.resolve("work"), root, root, null, 10_000, 100);
        CapturingContext context = new CapturingContext();
        CodebaseAnalystAgent agent = new CodebaseAnalystAgent(
                new CodebaseIndexer(props), new WorkspaceManager(props), context);

        agent.execute(new AgentContext("run-1", "brownfield", "fix it",
                "analyze-codebase", "Analyze", "Map impacted modules", 1));

        assertThat(context.paths).contains("codebase-index.md", "impact-analysis.json");
    }

    private static final class CapturingContext extends RunContext {
        final List<String> paths = new ArrayList<>();

        CapturingContext() {
            super(null, null);
        }

        @Override
        public Artifact put(String runId, String taskKey, ArtifactType type, String path, String content) {
            paths.add(path);
            return null;
        }
    }
}
