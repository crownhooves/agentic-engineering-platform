package com.example.agentic.orchestrator;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.agents.AgentType;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.domain.TaskNode;
import com.example.agentic.domain.repo.RunRepository;
import com.example.agentic.domain.repo.TaskNodeRepository;
import com.example.agentic.testsupport.RunAwaiter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recordingstest;DB_CLOSE_DELAY=-1",
        "agentic.llm.mock-latency=1ms"})
class UrlShortenerRecordingsTest {

    @Autowired WorkflowEngine engine;
    @Autowired RunRepository runs;
    @Autowired TaskNodeRepository nodes;

    @Test
    void recordedAnalysisAndPlanPassRealValidationAndExposeParallelism() {
        String id = engine.start("Build a scalable URL shortener service with APIs, persistence, and analytics.",
                "url-shortener").getId();
        RunAwaiter.await(runs, id, RunStatus.AWAITING_PLAN_APPROVAL, Duration.ofSeconds(20));

        List<TaskNode> planned = nodes.findByRunIdOrderById(id);
        TaskGraph graph = new TaskGraph(planned.stream()
                .map(n -> new TaskSpec(n.getTaskKey(), n.getTitle(), n.getAgent(), n.getDependsOn())).toList());

        assertThat(planned).extracting(TaskNode::getAgent).contains(AgentType.ARCHITECT, AgentType.CODER,
                AgentType.TEST_WRITER, AgentType.REVIEWER, AgentType.RISK, AgentType.SUMMARIZER);
        assertThat(graph.levels().get("implement-core")).isEqualTo(graph.levels().get("implement-analytics"));
        assertThat(graph.levels().get("assess-risks")).isEqualTo(graph.levels().get("implement-core"));
        assertThat(graph.levels().get("summarize")).isGreaterThan(graph.levels().get("validate-build"));
    }
}