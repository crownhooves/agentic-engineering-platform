package com.example.agentic.guardrails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Every agent-supplied path goes through here. Rejects traversal, absolute paths and symlink escapes. */
public final class PathGuard {

    private final Path root;

    public PathGuard(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() { return root; }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) throw violation("empty path");
        if (relativePath.indexOf('\0') >= 0) throw violation("NUL byte in path");
        Path candidate;
        try {
            candidate = Path.of(relativePath);
        } catch (InvalidPathException e) {
            throw violation("invalid path: " + relativePath);
        }
        if (candidate.isAbsolute()) throw violation("absolute paths are not allowed: " + relativePath);

        Path resolved = root.resolve(candidate).normalize();
        if (!resolved.startsWith(root)) throw violation("path escapes workspace: " + relativePath);
        rejectSymlinkEscape(resolved);
        return resolved;
    }

    /** Follows the nearest existing ancestor's real path and checks it is still under the real root. */
    private void rejectSymlinkEscape(Path resolved) {
        if (!Files.exists(root)) return;                       // nothing under a missing root can be a link
        try {
            Path existing = resolved;
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (existing == null) return;
            if (!existing.toRealPath().startsWith(root.toRealPath())) {
                throw violation("symlink escapes workspace: " + resolved);
            }
        } catch (IOException e) {
            throw violation("cannot verify path safely: " + e.getMessage());
        }
    }

    private static GuardrailViolationException violation(String msg) {
        return new GuardrailViolationException("Path guard: " + msg);
    }
}