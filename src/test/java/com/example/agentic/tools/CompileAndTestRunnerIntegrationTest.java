package com.example.agentic.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.agentic.config.WorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CompileAndTestRunnerIntegrationTest {

    @Test
    void referenceProjectCompilesAndRunsTests() {
        Path reference =
                Path.of("reference/sample")
                        .toAbsolutePath()
                        .normalize();

        Path root =
                Path.of("target/batch3-runner-test")
                        .toAbsolutePath()
                        .normalize();

        WorkspaceProperties props =
                new WorkspaceProperties(
                        root,
                        reference,
                        reference,
                        Duration.ofMinutes(2),
                        20_000,
                        100);

        WorkspaceManager manager =
                new WorkspaceManager(props);

        Path workspace =
                manager.prepare("integration-run");

        try {
            BuildResult result =
                    new CompileAndTestRunner(props)
                            .validate(workspace);

            assertThat(result.compileSucceeded())
                    .isTrue();

            assertThat(result.testsExecuted())
                    .isTrue();

            assertThat(result.success())
                    .isTrue();

            assertThat(result.testsRun())
                    .isGreaterThanOrEqualTo(1);

        } finally {
            manager.cleanup("integration-run");
        }

        assertThat(Files.exists(workspace))
                .isFalse();
    }
}

