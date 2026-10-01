package com.example.agentic.llm;

import com.example.agentic.common.InvalidOutputException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns raw model text into a validated DTO: finds the JSON document (skipping prose and markdown
 * fences), deserializes it leniently (unknown fields ignored), then runs semantic validation.
 */
public final class StructuredOutputParser {

    private final JsonMapper mapper;

    public StructuredOutputParser(JsonMapper mapper) { this.mapper = mapper; }

    public static StructuredOutputParser withDefaults() {
        return new StructuredOutputParser(JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build());
    }

    public <T> T parse(String raw, Class<T> type) {
        String json = extractJson(raw);
        T value;
        try {
            value = mapper.readValue(json, type);
        } catch (RuntimeException e) {
            throw new InvalidOutputException(
                    "Response is not a valid " + type.getSimpleName() + " JSON document: "
                            + firstLine(e.getMessage()), e);
        }
        if (value == null) throw new InvalidOutputException("Response JSON was empty (null)");
        if (value instanceof Validatable v) v.validate();
        return value;
    }

    /** Returns the first balanced {...} or [...] block, respecting string literals and escapes. */
    static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidOutputException("Empty response");
        int start = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{' || c == '[') { start = i; break; }
        }
        if (start < 0) throw new InvalidOutputException("No JSON object found in response");

        char open = raw.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false, escaped = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == open) depth++;
            else if (c == close && --depth == 0) return raw.substring(start, i + 1);
        }
        throw new InvalidOutputException("Unterminated JSON (the response may have been truncated)");
    }

    private static String firstLine(String s) {
        if (s == null) return "unknown error";
        int nl = s.indexOf('\n');
        String line = nl >= 0 ? s.substring(0, nl) : s;
        return line.length() > 300 ? line.substring(0, 300) + "..." : line;
    }
    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot serialize " + value.getClass().getSimpleName(), e);
        }
    }

}