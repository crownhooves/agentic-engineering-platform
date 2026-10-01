package com.example.agentic.guardrails;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Best-effort redaction applied to prompts sent to the LLM and to everything written to the audit log.
 * Deliberately conservative about assignments so that ordinary code such as
 * "String password = readFromVault();" is left alone.
 */
@Component
public class SecretRedactor {

    private static final String MASK = "[REDACTED]";

    private static final List<Pattern> TOKEN_PATTERNS = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}\\b"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{20,}"));

    private static final String KEYWORDS =
            "password|passwd|secret|api[_-]?key|access[_-]?token|auth[_-]?token";

    /** key = "literal"  (quoted literal of 8+ chars) */
    private static final Pattern QUOTED = Pattern.compile(
            "(?i)\\b(" + KEYWORDS + ")\\b(\\s*[:=]\\s*)([\"'])[^\"'\\s]{8,}\\3");

    /** config style: whole line "some.password=value" */
    private static final Pattern CONFIG_LINE = Pattern.compile(
            "(?im)^([ \\t]*[\\w.-]*(?:" + KEYWORDS + ")[\\w.-]*[ \\t]*[:=][ \\t]*)[^\\s\"'(;]{8,}[ \\t]*$");

    public String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        for (Pattern p : TOKEN_PATTERNS) out = p.matcher(out).replaceAll(MASK);
        out = QUOTED.matcher(out).replaceAll("$1$2$3" + MASK + "$3");
        out = CONFIG_LINE.matcher(out).replaceAll("$1" + MASK);
        return out;
    }
}