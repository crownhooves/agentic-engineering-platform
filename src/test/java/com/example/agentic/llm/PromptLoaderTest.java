package com.example.agentic.llm;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.common.NonRetryableException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptLoaderTest {

    @Test
    void substitutesVariables() {
        String out = PromptLoader.renderTemplate("Req: {{ requirement }} / {{lang}}",
                Map.of("requirement", "shorten urls", "lang", "Java"));
        assertThat(out).isEqualTo("Req: shorten urls / Java");
    }

    @Test
    void leavesSingleBracesAndDollarsUntouched() {
        String out = PromptLoader.renderTemplate("Return {\"a\": {{value}}} for $1",
                Map.of("value", "\"x$2\\y\""));
        assertThat(out).isEqualTo("Return {\"a\": \"x$2\\y\"} for $1");
    }

    @Test
    void failsFastOnMissingVariable() {
        assertThatThrownBy(() -> PromptLoader.renderTemplate("Hello {{name}}", Map.of()))
                .isInstanceOf(NonRetryableException.class).hasMessageContaining("name");
    }
}