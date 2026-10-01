package com.example.agentic.agents;

import com.example.agentic.agents.dto.AnalysisOutput;
import com.example.agentic.agents.dto.PlanOutput;
import com.example.agentic.common.NonRetryableException;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.domain.Run;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlannerAgent extends AbstractLlmAgent {

    public PlannerAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    public PlanOutput plan(Run run) {
        context.latestJson(run.getId(), ArtifactType.REQUIREMENT_ANALYSIS, AnalysisOutput.class)
                .orElseThrow(() -> new NonRetryableException(
                        "Cannot plan: no requirement analysis stored for run " + run.getId()));
        Map<String, String> vars = baseVars(run.getId(), run.getRequirement(), "Produce the task plan");
        return gateway.callJson(
                request(run.getId(), run.getScenario(), AgentType.PLANNER, "planner", "planner", vars, true),
                PlanOutput.class);
    }
}