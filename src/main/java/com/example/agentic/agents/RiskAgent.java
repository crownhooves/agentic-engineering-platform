package com.example.agentic.agents;

import com.example.agentic.agents.dto.RiskOutput;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RiskAgent extends AbstractLlmAgent implements Agent {

    public RiskAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    @Override public AgentType type() { return AgentType.RISK; }

    @Override
    public void execute(AgentContext ctx) {
        Map<String, String> vars = baseVars(ctx.runId(), ctx.requirement(), taskText(ctx));
        RiskOutput out = gateway.callJson(
                request(ctx.runId(), ctx.scenario(), AgentType.RISK, ctx.taskKey(), "risk", vars, true),
                RiskOutput.class);
        context.putJson(ctx.runId(), ctx.taskKey(), ArtifactType.RISK_REGISTER, "risk-register.json", out);
        context.put(ctx.runId(), ctx.taskKey(), ArtifactType.DOCUMENTATION, "docs/RISKS.md", out.toMarkdown());
    }
}