package com.example.agentic.tools;

import com.example.agentic.guardrails.GuardrailViolationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.example.agentic.config.WorkspaceProperties;

/**
 * Lightweight static indexer for brownfield reasoning.
 *
 * <p>It intentionally does not attempt to compile or execute the target code. It extracts
 * packages, Java types, common HTTP mappings, persistence indicators and important build/config
 * files, then emits a bounded index that can safely be supplied to an LLM.</p>
 */
@Component
public final class CodebaseIndexer {

    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([A-Za-z_][\\w.]*)\\s*;");
    private static final Pattern TYPE = Pattern.compile(
            "\\b(public\\s+)?(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern HTTP = Pattern.compile(
            "@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\s*(?:\\(\\s*\"([^\"]*)\"|)");
    private static final Pattern TABLE = Pattern.compile(
            "@(?:Entity|Table|Document|Repository|JdbcRepository)\\b|\\b(?:JpaRepository|CrudRepository)\\b");

    private static final Set<String> SOURCE_EXTENSIONS =
            Set.of(".java", ".kt", ".groovy", ".sql", ".yaml", ".yml", ".properties", ".xml");

    private final WorkspaceProperties properties;

    public CodebaseIndexer(WorkspaceProperties properties) {
        this.properties = properties;
    }

    public CodebaseIndex index(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Codebase root does not exist: " + normalized);
        }

        List<String> packages = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();
        List<String> persistence = new ArrayList<>();
        List<String> important = new ArrayList<>();
        int[] count = {0};

        try (var stream = Files.walk(normalized)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(p))
                    .limit(properties.maxFiles())
                    .forEach(path -> {
                        String relative = normalized.relativize(path).toString();
                        if (isImportant(relative)) important.add(relative);
                        String text = readBounded(path);
                        count[0]++;

                        Matcher m = PACKAGE.matcher(text);
                        while (m.find() && packages.size() < 500) addUnique(packages, m.group(1));

                        m = TYPE.matcher(text);
                        while (m.find() && types.size() < 1000) {
                            addUnique(types, relative + " :: " + m.group(3) + " (" + m.group(2) + ")");
                        }

                        m = HTTP.matcher(text);
                        while (m.find() && endpoints.size() < 500) {
                            String route = m.group(1);
                            addUnique(endpoints, relative + " :: " + (route == null ? "/" : route));
                        }

                        if (TABLE.matcher(text).find() && persistence.size() < 500) {
                            addUnique(persistence, relative);
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot index codebase " + normalized, e);
        }

        packages.sort(String::compareTo);
        types.sort(String::compareTo);
        endpoints.sort(String::compareTo);
        persistence.sort(String::compareTo);
        important.sort(String::compareTo);

        return new CodebaseIndex(normalized, count[0], packages, types, endpoints, persistence, important);
    }

    private String readBounded(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int max = Math.min(bytes.length, 20_000);
            return new String(bytes, 0, max, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GuardrailViolationException("Cannot read codebase file: " + file);
        }
    }

    private static boolean isIgnored(Path path) {
        for (Path part : path) {
            String p = part.toString();
            if (p.equals(".git") || p.equals("target") || p.equals("node_modules")
                    || p.equals(".idea") || p.equals(".gradle")) return true;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return !(SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith)
                || name.equals("pom.xml")
                || name.equals("build.gradle")
                || name.equals("settings.gradle"));
    }

    private static boolean isImportant(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        return lower.equals("pom.xml") || lower.equals("build.gradle")
                || lower.contains("application.yml") || lower.contains("application.yaml")
                || lower.contains("application.properties")
                || lower.contains("dockerfile") || lower.contains("openapi")
                || lower.contains("schema") || lower.contains("migration");
    }

    private static void addUnique(List<String> list, String value) {
        if (!list.contains(value)) list.add(value);
    }
}
