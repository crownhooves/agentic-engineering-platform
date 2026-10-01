package com.example.agentic.agents;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.common.InvalidOutputException;
import org.junit.jupiter.api.Test;

class FileBundleParserTest {

    @Test
    void parsesMultipleFiles() {
        String raw = "=== FILE: a/A.java ===\nclass A {}\n=== END FILE ===\n"
                + "=== FILE: b/B.java ===\nclass B {}\n=== END FILE ===\n";
        FileBundle b = FileBundleParser.parse(raw);
        assertThat(b.files()).hasSize(2);
        assertThat(b.files().get(0).path()).isEqualTo("a/A.java");
        assertThat(b.files().get(0).content()).isEqualTo("class A {}\n");
    }

    @Test
    void stripsMarkdownFencesInsideBlocks() {
        String raw = "=== FILE: A.java ===\n```java\nclass A {}\n```\n=== END FILE ===";
        assertThat(FileBundleParser.parse(raw).files().get(0).content()).isEqualTo("class A {}\n");
    }

    @Test
    void ignoresProseOutsideBlocks() {
        String raw = "Sure, here are the files:\n=== FILE: A.java ===\nclass A {}\n=== END FILE ===\nDone!";
        assertThat(FileBundleParser.parse(raw).files()).hasSize(1);
    }

    @Test
    void toleratesAMissingEndMarkerBetweenFiles() {
        String raw = "=== FILE: A.java ===\nclass A {}\n=== FILE: B.java ===\nclass B {}\n=== END FILE ===";
        assertThat(FileBundleParser.parse(raw).files()).extracting(FileBundle.GeneratedFile::path)
                .containsExactly("A.java", "B.java");
    }

    @Test
    void rejectsATruncatedLastFile() {
        assertThatThrownBy(() -> FileBundleParser.parse("=== FILE: A.java ===\nclass A {"))
                .isInstanceOf(InvalidOutputException.class).hasMessageContaining("END FILE");
    }

    @Test
    void rejectsResponsesWithoutFiles() {
        assertThatThrownBy(() -> FileBundleParser.parse("Here is your code, enjoy"))
                .isInstanceOf(InvalidOutputException.class).hasMessageContaining("No '=== FILE");
    }

    @Test
    void rejectsDuplicatePathsAndEmptyFiles() {
        String dup = "=== FILE: A.java ===\nx\n=== END FILE ===\n=== FILE: A.java ===\ny\n=== END FILE ===";
        assertThatThrownBy(() -> FileBundleParser.parse(dup)).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> FileBundleParser.parse("=== FILE: A.java ===\n\n=== END FILE ==="))
                .hasMessageContaining("empty");
    }
}