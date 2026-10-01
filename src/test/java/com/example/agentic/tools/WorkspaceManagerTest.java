package com.example.agentic.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.agentic.config.WorkspaceProperties;
import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceManagerTest {

    @Test
    void materializesOnlyAllowedArtifactTypesAndKeepsPathsInsideWorkspace() throws Exception {
        Path root = Files.createTempDirectory("workspace-root");
        Path reference = Files.createTempDirectory("reference-root");
        Files.writeString(reference.resolve("pom.xml"), "<project/>");

        WorkspaceManager manager = new WorkspaceManager(
                new WorkspaceProperties(root, reference, reference, null, 10_000, 20));

        Artifact source = new Artifact("run-1", "coder", ArtifactType.SOURCE_CODE,
                "src/main/java/A.java", "class A {}\n", 1);
        Artifact docs = new Artifact("run-1", "architect", ArtifactType.DOCUMENTATION,
                "docs/x.md", "ignored", 1);

        Path workspace = manager.materialize("run-1", List.of(source, docs));

        assertThat(Files.readString(workspace.resolve("src/main/java/A.java"))).contains("class A");
        assertThat(Files.exists(workspace.resolve("docs/x.md"))).isFalse();
        assertThatThrownBy(() -> manager.materialize("run-1", List.of(
                new Artifact("run-1", "coder", ArtifactType.SOURCE_CODE,
                        "../escape.java", "bad", 1))))
                .isInstanceOf(RuntimeException.class);
    }
}
