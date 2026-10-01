package com.example.agentic.llm;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.testsupport.Sample;
import org.junit.jupiter.api.Test;

class StructuredOutputParserTest {

    private final StructuredOutputParser parser = StructuredOutputParser.withDefaults();

    @Test
    void parsesPlainJson() {
        Sample s = parser.parse("{\"name\":\"a\",\"count\":2}", Sample.class);
        assertThat(s).isEqualTo(new Sample("a", 2));
    }

    @Test
    void stripsMarkdownFencesAndProse() {
        String raw = "Sure! Here you go:\n```json\n{\"name\":\"x\",\"count\":1}\n```\nHope that helps.";
        assertThat(parser.parse(raw, Sample.class).name()).isEqualTo("x");
    }

    @Test
    void handlesBracesAndQuotesInsideStrings() {
        String raw = "{\"name\":\"a } b { c \\\" d\",\"count\":1}";
        assertThat(parser.parse(raw, Sample.class).name()).isEqualTo("a } b { c \" d");
    }

    @Test
    void ignoresUnknownFields() {
        Sample s = parser.parse("{\"name\":\"a\",\"count\":1,\"extra\":true}", Sample.class);
        assertThat(s.name()).isEqualTo("a");
    }

    @Test
    void rejectsResponseWithoutJson() {
        assertThatThrownBy(() -> parser.parse("I cannot help with that.", Sample.class))
                .isInstanceOf(InvalidOutputException.class).hasMessageContaining("No JSON");
    }

    @Test
    void detectsTruncatedJson() {
        assertThatThrownBy(() -> parser.parse("{\"name\":\"x\",\"cou", Sample.class))
                .isInstanceOf(InvalidOutputException.class).hasMessageContaining("truncated");
    }

    @Test
    void rejectsWrongFieldType() {
        assertThatThrownBy(() -> parser.parse("{\"name\":\"x\",\"count\":\"many\"}", Sample.class))
                .isInstanceOf(InvalidOutputException.class);
    }

    @Test
    void runsSemanticValidation() {
        assertThatThrownBy(() -> parser.parse("{\"count\":3}", Sample.class))
                .isInstanceOf(InvalidOutputException.class).hasMessageContaining("name");
    }
}