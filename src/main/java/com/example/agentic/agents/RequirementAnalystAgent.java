package com.example.agentic.agents;

import com.example.agentic.agents.dto.AnalysisOutput;
import com.example.agentic.domain.Run;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequirementAnalystAgent extends AbstractLlmAgent {

    public RequirementAnalystAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    public AnalysisOutput analyze(Run run) {
        return gateway.callJson(
                request(run.getId(), run.getScenario(), AgentType.REQUIREMENT_ANALYST, "analyst", "analyst",
                        Map.of("requirement", run.getRequirement()), true),
                AnalysisOutput.class);
    }
}