package com.example.agentic.agents;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import org.springframework.stereotype.Component;

@Component
public class CoderAgent extends CodeAgent {

    public CoderAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    @Override public AgentType type() { return AgentType.CODER; }
    @Override protected String promptName() { return "coder"; }
    @Override protected ArtifactType artifactType() { return ArtifactType.SOURCE_CODE; }

    @Override
    protected void checkPath(String path) {
        if (path.startsWith("src/test/")) {
            throw new InvalidOutputException("The coder must not write tests; '" + path
                    + "' belongs to the test writer. Only create files outside src/test/.");
        }
    }
}