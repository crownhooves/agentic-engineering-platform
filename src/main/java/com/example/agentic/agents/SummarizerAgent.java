package com.example.agentic.agents;

import com.example.agentic.agents.dto.EngineeringSummary;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SummarizerAgent extends AbstractLlmAgent implements Agent {

    public SummarizerAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    @Override public AgentType type() { return AgentType.SUMMARIZER; }

    @Override
    public void execute(AgentContext ctx) {
        String index = context.index(ctx.runId());
        Map<String, String> vars = baseVars(ctx.runId(), ctx.requirement(), taskText(ctx));
        vars.put("artifactIndex", index);
        EngineeringSummary out = gateway.callJson(
                request(ctx.runId(), ctx.scenario(), AgentType.SUMMARIZER, ctx.taskKey(), "summarizer", vars, true),
                EngineeringSummary.class);
        context.put(ctx.runId(), ctx.taskKey(), ArtifactType.SUMMARY, "SUMMARY.md", out.toMarkdown(index));
    }
}