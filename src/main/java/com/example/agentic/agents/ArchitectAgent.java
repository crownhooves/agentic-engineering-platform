package com.example.agentic.agents;

import com.example.agentic.agents.dto.ArchitectureOutput;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArchitectAgent extends AbstractLlmAgent implements Agent {

    public ArchitectAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    @Override public AgentType type() { return AgentType.ARCHITECT; }

    @Override
    public void execute(AgentContext ctx) {
        Map<String, String> vars = baseVars(ctx.runId(), ctx.requirement(), taskText(ctx));
        ArchitectureOutput out = gateway.callJson(
                request(ctx.runId(), ctx.scenario(), AgentType.ARCHITECT, ctx.taskKey(), "architect", vars, true),
                ArchitectureOutput.class);
        context.putJson(ctx.runId(), ctx.taskKey(), ArtifactType.ARCHITECTURE, "architecture.json", out);
        context.put(ctx.runId(), ctx.taskKey(), ArtifactType.OPENAPI, "openapi.yaml", out.openApiYaml());
        context.put(ctx.runId(), ctx.taskKey(), ArtifactType.DOCUMENTATION, "docs/ARCHITECTURE.md", out.toMarkdown());
    }
}