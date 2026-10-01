package com.example.agentic.agents;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.domain.ArtifactType;
import com.example.agentic.llm.LlmGateway;
import com.example.agentic.llm.PromptLoader;
import com.example.agentic.orchestrator.RunContext;
import org.springframework.stereotype.Component;

@Component
public class TestWriterAgent extends CodeAgent {

    public TestWriterAgent(LlmGateway gateway, PromptLoader prompts, RunContext context) {
        super(gateway, prompts, context);
    }

    @Override public AgentType type() { return AgentType.TEST_WRITER; }
    @Override protected String promptName() { return "test-writer"; }
    @Override protected ArtifactType artifactType() { return ArtifactType.TEST_CODE; }

    @Override
    protected void checkPath(String path) {
        if (!path.startsWith("src/test/")) {
            throw new InvalidOutputException("The test writer may only create files under src/test/, not '" + path + "'");
        }
    }
}