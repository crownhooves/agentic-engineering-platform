package com.example.agentic.agents;

import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.LlmRequest;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import java.util.Map;

/** Template for agents that emit source files. Subclasses only say which prompt, artifact type and paths. */
public abstract class CodeAgent extends AbstractLlmAgent implements Agent {

    private static final int MAX_EXISTING_CODE_CHARS = 30_000;

    protected CodeAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    protected abstract String promptName();
    protected abstract ArtifactType artifactType();
    /** Throw InvalidOutputException if this agent may not write the given path. */
    protected abstract void checkPath(String path);

    @Override
    public void execute(AgentContext ctx) {
        Map<String, String> vars = baseVars(ctx.runId(), ctx.requirement(), taskText(ctx));
        vars.put("existingCode", existingCode(ctx.runId()));
        LlmRequest req = request(ctx.runId(), ctx.scenario(), type(), ctx.taskKey(), promptName(), vars, false);

        FileBundle bundle = gateway.callParsed(req, this::parseAndCheck, "file bundle",
                "Return ONLY the corrected files, each in an '=== FILE: <path> ===' ... "
                        + "'=== END FILE ===' block. No JSON, no commentary.");
        for (FileBundle.GeneratedFile f : bundle.files()) {
            context.put(ctx.runId(), ctx.taskKey(), artifactType(), f.path(), f.content());
        }
    }

    private FileBundle parseAndCheck(String raw) {
        FileBundle bundle = FileBundleParser.parse(raw);
        for (FileBundle.GeneratedFile f : bundle.files()) {
            RunContext.requireSafePath(f.path());
            checkPath(f.path());
        }
        return bundle;
    }

    private String existingCode(String runId) {
        StringBuilder sb = new StringBuilder();
        for (Artifact a : context.currentFiles(runId, ArtifactType.SOURCE_CODE, ArtifactType.TEST_CODE)) {
            String block = "=== FILE: " + a.getPath() + " ===\n" + a.getContent() + "=== END FILE ===\n";
            if (sb.length() + block.length() > MAX_EXISTING_CODE_CHARS) {
                sb.append("... (remaining files omitted)\n");
                break;
            }
            sb.append(block);
        }
        return sb.length() == 0 ? "(no existing code)" : sb.toString();
    }
}