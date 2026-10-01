package com.example.agentic.llm;

import com.example.agentic.common.NonRetryableException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads classpath:prompts/{name}.txt and substitutes {{variable}} placeholders in a single pass.
 * Single braces (JSON, code) are never touched; a missing variable fails fast.
 */
@Component
public class PromptLoader {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public record Rendered(String system, String user) {}

    private static final String USER_MARKER = "--- USER ---";

    /** A prompt file has a system section, a line "--- USER ---", then the user section. */
    public Rendered renderSections(String promptName, Map<String, String> variables) {
        String full = load(promptName);
        int idx = full.indexOf(USER_MARKER);
        if (idx < 0) throw new NonRetryableException("Prompt " + promptName + " has no '" + USER_MARKER + "' line");
        String system = full.substring(0, idx).strip();
        String user = full.substring(idx + USER_MARKER.length()).strip();
        return new Rendered(renderTemplate(system, variables), renderTemplate(user, variables));
    }

    public String render(String promptName, Map<String, String> variables) {
        return renderTemplate(load(promptName), variables);
    }

    public String load(String promptName) {
        return cache.computeIfAbsent(promptName, n -> {
            ClassPathResource r = new ClassPathResource("prompts/" + n + ".txt");
            if (!r.exists()) throw new NonRetryableException("Missing prompt template prompts/" + n + ".txt");
            try {
                return r.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read prompt " + n, e);
            }
        });
    }

    public static String renderTemplate(String template, Map<String, String> variables) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = variables.get(m.group(1));
            if (value == null) throw new NonRetryableException("Prompt variable not provided: " + m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }
}