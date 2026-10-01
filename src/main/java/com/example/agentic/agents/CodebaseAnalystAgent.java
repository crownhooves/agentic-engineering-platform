package com.example.agentic.agents;

import com.example.agentic.domain.ArtifactType;
import com.example.agentic.orchestrator.RunContext;
import com.example.agentic.tools.CodebaseIndex;
import com.example.agentic.tools.CodebaseIndexer;
import com.example.agentic.tools.WorkspaceManager;
import org.springframework.stereotype.Component;

/**
 * Brownfield-only agent that converts a real source tree into a bounded architectural index.
 *
 * <p>The configured brownfield root is intentionally external to the generated workspace so a
 * run cannot overwrite the system it is analysing.</p>
 */
@Component
public final class CodebaseAnalystAgent implements Agent {

    private final CodebaseIndexer indexer;
    private final WorkspaceManager workspaceManager;
    private final RunContext context;

    public CodebaseAnalystAgent(CodebaseIndexer indexer,
                                WorkspaceManager workspaceManager,
                                RunContext context) {
        this.indexer = indexer;
        this.workspaceManager = workspaceManager;
        this.context = context;
    }

    @Override
    public AgentType type() {
        return AgentType.CODEBASE_ANALYST;
    }

    @Override
    public void execute(AgentContext ctx) {
        CodebaseIndex index = indexer.index(workspaceManager.brownfieldRoot());
        context.put(ctx.runId(), ctx.taskKey(), ArtifactType.IMPACT_ANALYSIS,
                "codebase-index.md", index.toMarkdown());

        String json = """
                {
                  "root": "%s",
                  "fileCount": %d,
                  "packages": %s,
                  "types": %s,
                  "endpoints": %s,
                  "persistenceHints": %s,
                  "importantFiles": %s
                }
                """.formatted(
                escape(index.root().toString()),
                index.fileCount(),
                array(index.packages()),
                array(index.types()),
                array(index.endpoints()),
                array(index.persistenceHints()),
                array(index.importantFiles()));

        context.put(ctx.runId(), ctx.taskKey(), ArtifactType.IMPACT_ANALYSIS,
                "impact-analysis.json", json);
    }

    private static String array(java.util.List<String> values) {
        return "[" + values.stream().map(CodebaseAnalystAgent::quote).collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
