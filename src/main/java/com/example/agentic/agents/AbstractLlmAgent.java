package com.example.agentic.agents;

import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.LlmRequest;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import java.util.HashMap;
import java.util.Map;

/** Shared plumbing: render a prompt file and expose the blackboard as prompt variables. */
public abstract class AbstractLlmAgent {

    protected final LlmGateway gateway;
    protected final PromptLoader prompts;
    protected final RunContext context;

    protected AbstractLlmAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.context = context;
    }

    protected LlmRequest request(String runId, String scenario, AgentType agent, String callKey,
                                 String promptName, Map<String, String> vars, boolean jsonMode) {
        PromptLoader.Rendered p = prompts.renderSections(promptName, vars);
        return new LlmRequest(runId, scenario, agent, callKey, p.system(), p.user(), jsonMode);
    }

    /** Everything an agent may want to see; templates only use the variables they mention. */
    protected Map<String, String> baseVars(String runId, String requirement, String taskText) {
        Map<String, String> v = new HashMap<>();
        v.put("requirement", requirement);
        v.put("task", taskText);
        v.put("analysis", contentOrNone(runId, ArtifactType.REQUIREMENT_ANALYSIS));
        v.put("answers", contentOrNone(runId, ArtifactType.CLARIFICATION_ANSWERS));
        v.put("impact", contentOrNone(runId, ArtifactType.IMPACT_ANALYSIS));
        v.put("architecture", contentOrNone(runId, ArtifactType.ARCHITECTURE));
        v.put("openapi", contentOrNone(runId, ArtifactType.OPENAPI));
        v.put("risks", contentOrNone(runId, ArtifactType.RISK_REGISTER));
        v.put("validation", contentOrNone(runId, ArtifactType.VALIDATION_REPORT));
        return v;
    }

    protected static String taskText(AgentContext ctx) {
        return ctx.taskTitle() + "\n" + (ctx.taskDescription() == null ? "" : ctx.taskDescription());
    }

    private String contentOrNone(String runId, ArtifactType type) {
        return context.latest(runId, type).map(Artifact::getContent).orElse("(none)");
    }
}