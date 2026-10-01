package com.example.agentic.agents;

import com.example.agentic.domain.ArtifactType;
import com.example.agentic.orchestrator.RunContext;
import com.example.agentic.tools.RepairLoop;
import org.springframework.stereotype.Component;

/**
 * Deterministic reviewer agent. It validates the actual generated workspace rather than asking an
 * LLM whether the code looks correct, then records machine-verifiable evidence.
 */
@Component
public final class ReviewerAgent implements Agent {

    private final RepairLoop repairLoop;
    private final RunContext context;

    public ReviewerAgent(RepairLoop repairLoop, RunContext context) {
        this.repairLoop = repairLoop;
        this.context = context;
    }

    @Override
    public AgentType type() {
        return AgentType.REVIEWER;
    }

    @Override
    public void execute(AgentContext ctx) throws Exception {
        repairLoop.validateAndRepair(ctx.runId(), ctx);
        // The RepairLoop owns validation-report.json. This additional marker is deliberately
        // small and gives the final summary a human-readable fact that validation completed.
        context.put(ctx.runId(), ctx.taskKey(), ArtifactType.DOCUMENTATION,
                "docs/VALIDATION.md",
                "# Validation\n\nCompilation and the test suite completed successfully after the configured repair loop.");
    }
}
