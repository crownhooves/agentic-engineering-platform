package com.example.agentic.agents;

import com.example.agentic.common.InvalidOutputException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses "=== FILE: path ===" ... "=== END FILE ===" blocks. Prose outside blocks is ignored, markdown
 * fences inside a block are stripped, and a missing END marker between two files is tolerated (small
 * models forget it). A missing END on the LAST file means the response was truncated and is rejected.
 */
public final class FileBundleParser {

    private static final Pattern HEADER = Pattern.compile("^===\\s*FILE:\\s*(.+?)\\s*===\\s*$");
    private static final Pattern END = Pattern.compile("^===\\s*END FILE\\s*===\\s*$");

    private FileBundleParser() {}

    public static FileBundle parse(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidOutputException("Empty response");
        List<FileBundle.GeneratedFile> files = new ArrayList<>();
        String path = null;
        List<String> body = new ArrayList<>();

        for (String line : raw.split("\\R", -1)) {
            String trimmed = line.strip();
            Matcher header = HEADER.matcher(trimmed);
            if (header.matches()) {
                if (path != null) files.add(finish(path, body));
                path = header.group(1);
                body = new ArrayList<>();
            } else if (END.matcher(trimmed).matches()) {
                if (path != null) files.add(finish(path, body));
                path = null;
                body = new ArrayList<>();
            } else if (path != null) {
                body.add(line);
            }
        }
        if (path != null) {
            throw new InvalidOutputException("File '" + path
                    + "' has no '=== END FILE ===' marker (the response may have been truncated)");
        }
        if (files.isEmpty()) throw new InvalidOutputException("No '=== FILE: <path> ===' blocks found");

        Set<String> seen = new HashSet<>();
        for (FileBundle.GeneratedFile f : files) {
            if (!seen.add(f.path())) throw new InvalidOutputException("Duplicate file path: " + f.path());
        }
        return new FileBundle(List.copyOf(files));
    }

    private static FileBundle.GeneratedFile finish(String path, List<String> lines) {
        int start = 0, end = lines.size();
        while (start < end && lines.get(start).isBlank()) start++;
        while (end > start && lines.get(end - 1).isBlank()) end--;
        if (start < end && lines.get(start).strip().startsWith("```")) start++;
        if (end > start && lines.get(end - 1).strip().equals("```")) end--;
        String content = String.join("\n", lines.subList(start, end)).stripTrailing();
        if (content.isBlank()) throw new InvalidOutputException("File '" + path + "' is empty");
        return new FileBundle.GeneratedFile(path, content + "\n");
    }
}