package com.example.agentic.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agentic.agents.AgentContext;
import com.example.agentic.config.WorkspaceProperties;
import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.orchestrator.RunContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RepairLoopTest {

    @Test
    void repairClientIsInvokedWhenBuildFailsAndValidationArtifactIsUpdated() throws Exception {
        Path reference = Files.createTempDirectory("repair-reference");
        Files.writeString(reference.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>x</groupId><artifactId>x</artifactId><version>1</version>
                  <properties><maven.compiler.release>17</maven.compiler.release></properties>
                </project>
                """);
        Files.createDirectories(reference.resolve("src/main/java/x"));

        Path root = Files.createTempDirectory("repair-work");
        WorkspaceProperties props = new WorkspaceProperties(
                root, reference, reference, java.time.Duration.ofMinutes(1), 10_000, 50);

        FakeRunContext context = new FakeRunContext();
        WorkspaceManager manager = new WorkspaceManager(props);
        AtomicInteger repairs = new AtomicInteger();

        RepairLoop loop = new RepairLoop(
                manager,
                workspace -> new BuildResult(false, false, false, 1, 0, 1,
                        "cannot find symbol", java.time.Duration.ZERO),
                context,
                (ctx, result, attempt) -> repairs.incrementAndGet(),
                2);

        assertThatThrownBy(() -> loop.validateAndRepair(
                "run-1",
                new AgentContext("run-1", "custom", "req", "review",
                        "Review", "compile and test", 1)))
                .isInstanceOf(com.example.agentic.common.EscalationRequiredException.class);

        assertThat(repairs.get()).isEqualTo(2);
        assertThat(context.puts).hasSize(3);
    }

    private static final class FakeRunContext extends RunContext {
        final List<String> puts = new java.util.ArrayList<>();

        FakeRunContext() {
            super(null, null);
        }

        @Override
        public List<Artifact> currentFiles(String runId, ArtifactType... types) {
            return List.of();
        }

        @Override
        public Artifact put(String runId, String taskKey, ArtifactType type, String path, String content) {
            puts.add(path);
            return null;
        }
    }
}
