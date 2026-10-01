package com.example.agentic.guardrails;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathGuardTest {

    @TempDir Path tmp;
    private Path root;
    private PathGuard guard;

    @BeforeEach
    void setUp() throws IOException {
        root = tmp.resolve("workspace");
        Files.createDirectories(root);
        guard = new PathGuard(root);
    }

    @Test
    void allowsNestedRelativePaths() {
        assertThat(guard.resolve("src/main/A.java")).isEqualTo(guard.root().resolve("src/main/A.java"));
    }

    @Test
    void allowsDotDotThatStaysInside() {
        assertThat(guard.resolve("a/../b/c.txt")).isEqualTo(guard.root().resolve("b/c.txt"));
    }

    @Test
    void rejectsTraversal() {
        assertThatThrownBy(() -> guard.resolve("../outside.txt")).isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    void rejectsDeepTraversal() {
        assertThatThrownBy(() -> guard.resolve("a/b/../../../outside"))
                .isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    void rejectsAbsolutePaths() {
        assertThatThrownBy(() -> guard.resolve("/etc/passwd")).isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    void rejectsBlankAndNull() {
        assertThatThrownBy(() -> guard.resolve("  ")).isInstanceOf(GuardrailViolationException.class);
        assertThatThrownBy(() -> guard.resolve(null)).isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    void rejectsSymlinkEscape() throws IOException {
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        try {
            Files.createSymbolicLink(root.resolve("link"), outside);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symlinks not supported here");
        }
        assertThatThrownBy(() -> guard.resolve("link/secret.txt"))
                .isInstanceOf(GuardrailViolationException.class).hasMessageContaining("symlink");
    }
}