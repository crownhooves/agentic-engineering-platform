package com.example.agentic.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentic.config.WorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CodebaseIndexerTest {

    @Test
    void indexesJavaTypesEndpointsAndPersistenceHints() throws Exception {
        Path root = Files.createTempDirectory("indexer-test");
        Files.createDirectories(root.resolve("src/main/java/x"));
        Files.writeString(root.resolve("src/main/java/x/App.java"), """
                package x;
                @Entity
                @Table(name="links")
                public class App {
                  @GetMapping("/hello")
                  public String hello() { return "ok"; }
                }
                """);

        WorkspaceProperties props = new WorkspaceProperties(
                root.resolve("work"), root, root, null, 10_000, 100);
        CodebaseIndex index = new CodebaseIndexer(props).index(root);

        assertThat(index.fileCount()).isEqualTo(1);
        assertThat(index.packages()).contains("x");
        assertThat(index.types()).anyMatch(v -> v.contains("App"));
        assertThat(index.endpoints()).anyMatch(v -> v.contains("/hello"));
        assertThat(index.persistenceHints()).contains("src/main/java/x/App.java");
    }
}
