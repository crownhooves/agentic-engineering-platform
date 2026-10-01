package com.example.agentic.agents;

import com.example.agentic.agents.FileBundle.GeneratedFile;
import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.LlmRequest;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import com.example.agentic.tools.BuildResult;
import com.example.agentic.tools.RepairLoop;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * LLM-backed repair adapter used only by RepairLoop.
 *
 * <p>It reuses the normal coder constraints and the same guarded LLM gateway, but gives every
 * repair a distinct call key so the audit trail and deterministic recordings remain observable.</p>
 */
@Component
public final class CodeRepairClient implements RepairLoop.RepairClient {

    private final LlmGateway gateway;
    private final PromptLoader prompts;
    private final RunContext context;

    public CodeRepairClient(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.context = context;
    }

    @Override
    public void repair(AgentContext ctx, BuildResult failure, int repairAttempt) {
        Map<String, String> vars = new HashMap<>();
        vars.put("requirement", ctx.requirement());
        vars.put("task", ctx.taskTitle() + "\n" + ctx.taskDescription());
        vars.put("validation", failure.output());
        vars.put("existingCode", existingCode(ctx.runId()));

        PromptLoader.Rendered rendered = prompts.renderSections("repair", vars);
        LlmRequest request = new LlmRequest(
                ctx.runId(),
                ctx.scenario(),
                AgentType.CODER,
                ctx.taskKey() + "-repair-" + repairAttempt,
                rendered.system(),
                rendered.user(),
                false);

        FileBundle bundle = gateway.callParsed(
                request,
                raw -> parseAndCheck(raw),
                "repair file bundle",
                "Return ONLY corrected files in FILE/END FILE blocks. Do not add commentary.");

        for (GeneratedFile file : bundle.files()) {
            context.put(ctx.runId(), ctx.taskKey(), ArtifactType.SOURCE_CODE,
                    file.path(), file.content());
        }
    }

    private FileBundle parseAndCheck(String raw) {
        FileBundle bundle = FileBundleParser.parse(raw);
        for (GeneratedFile file : bundle.files()) {
            RunContext.requireSafePath(file.path());
            if (file.path().startsWith("src/test/")) {
                throw new InvalidOutputException("Repair coder may not modify tests: " + file.path());
            }
        }
        return bundle;
    }

    private String existingCode(String runId) {
        StringBuilder out = new StringBuilder();
        for (var artifact : context.currentFiles(runId,
                ArtifactType.SOURCE_CODE, ArtifactType.TEST_CODE)) {
            String block = "=== FILE: " + artifact.getPath() + " ===\n"
                    + artifact.getContent() + "=== END FILE ===\n";
            if (out.length() + block.length() > 30_000) {
                out.append("... (remaining files omitted)\n");
                break;
            }
            out.append(block);
        }
        return out.length() == 0 ? "(no existing code)" : out.toString();
    }
}
