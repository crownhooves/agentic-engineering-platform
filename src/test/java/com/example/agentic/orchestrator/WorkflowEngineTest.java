package com.example.agentic.orchestrator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.agentic.domain.*;
import com.example.agentic.domain.repo.*;
import com.example.agentic.testsupport.MapLlmClient;
import com.example.agentic.testsupport.RunAwaiter;
import com.example.agentic.tools.RuntimeVerificationResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:enginetest;DB_CLOSE_DELAY=-1",
        "agentic.orchestrator.initial-backoff=1ms",
        "agentic.orchestrator.max-backoff=5ms",
        "agentic.llm.mock-latency=1ms"})
class WorkflowEngineTest {

    @TestConfiguration
    static class Config {

        @Bean
        @Primary
        MapLlmClient mapLlmClient() {
            return new MapLlmClient();
        }
    }

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static final String ANALYSIS_OK = """
            {"normalizedRequirement":"Build a tiny service","workType":"GREENFIELD",
             "functionalRequirements":["shorten urls"],"nonFunctionalRequirements":[],
             "ambiguities":[],"assumptions":[],"outOfScope":[]}""";

    private static final String ANALYSIS_BLOCKING = """
            {"normalizedRequirement":"Improve the thing","workType":"BROWNFIELD",
             "functionalRequirements":["improve it"],"nonFunctionalRequirements":[],
             "ambiguities":[{"question":"Which service?","impact":"scope","assumedAnswer":"","blocking":true}],
             "assumptions":[],"outOfScope":[]}""";

    private static final String PLAN_OK = """
            {"rationale":"r","tasks":[
             {"key":"arch","title":"Architecture","description":"d","agent":"ARCHITECT","dependsOn":[],"risk":"LOW","estimateHours":1},
             {"key":"risk","title":"Risks","description":"d","agent":"RISK","dependsOn":["arch"],"risk":"LOW","estimateHours":1},
             {"key":"summary","title":"Summary","description":"d","agent":"SUMMARIZER","dependsOn":["arch","risk"],"risk":"LOW","estimateHours":1}]}""";

    private static final String ARCH_OK = """
            {"overview":"o","components":[{"name":"C","responsibility":"r","contract":"c"}],
             "dataModel":[{"name":"E","fields":["id"]}],"openApiYaml":"openapi: 3.0.3 paths: {}",
             "decisions":[{"decision":"d","rationale":"r","tradeoffs":"t"}],"scalabilityNotes":[]}""";

    private static final String RISK_OK = """
            {"risks":[{"id":"R1","description":"d","likelihood":"LOW","impact":"low","mitigation":"m"}],
             "failureScenarios":[{"scenario":"s","detection":"d","mitigation":"m"}],
             "validationStrategy":["unit tests"],"tradeoffs":["t"]}""";

    private static final String SUMMARY_OK = """
            {"implementationPlan":"p","rationale":"r","risksAndTradeoffs":["x"],
             "validationApproach":"v","assumptions":["a"],"limitations":["l"]}""";

    @Autowired
    WorkflowEngine engine;

    @Autowired
    ApprovalService approvals;

    @Autowired
    RunRepository runs;

    @Autowired
    TaskNodeRepository nodes;

    @Autowired
    ArtifactRepository artifacts;

    @Autowired
    AuditRecordRepository audit;

    @Autowired
    MapLlmClient llm;

    /**
     * WorkflowEngineTest validates orchestration and human gates.
     *
     * Runtime verification is tested separately by RuntimeVerificationServiceTest
     * and should not require generated source artifacts in this orchestration test.
     */
    @MockitoBean
    RuntimeVerificationStep runtimeVerificationStep;

    @BeforeEach
    void script() {
        llm.clear();

        llm.put("analyst", ANALYSIS_OK)
                .put("planner", PLAN_OK)
                .put("arch", ARCH_OK)
                .put("risk", RISK_OK)
                .put("summary", SUMMARY_OK);

        when(runtimeVerificationStep.verify(anyString()))
                .thenReturn(
                        RuntimeVerificationResult.passed(
                                0,
                                Duration.ZERO,
                                List.of()));
    }

    @Test
    void happyPathPassesBothHumanGates() {
        String id = engine.start("Build a tiny service", "test").getId();

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_PLAN_APPROVAL,
                WAIT);

        assertThat(nodes.findByRunIdOrderById(id))
                .hasSize(3)
                .allMatch(n -> n.getStatus() == TaskStatus.PENDING);

        approvals.approvePlan(id, Set.of());

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_REVIEW,
                WAIT);

        assertThat(nodes.findByRunIdOrderById(id))
                .allMatch(n -> n.getStatus() == TaskStatus.SUCCEEDED);

        assertThat(artifacts.findByRunIdOrderByCreatedAtAsc(id))
                .extracting(Artifact::getType)
                .contains(
                        ArtifactType.REQUIREMENT_ANALYSIS,
                        ArtifactType.PLAN,
                        ArtifactType.ARCHITECTURE,
                        ArtifactType.OPENAPI,
                        ArtifactType.RISK_REGISTER,
                        ArtifactType.SUMMARY);

        approvals.approveResult(id);

        RunAwaiter.await(
                runs,
                id,
                RunStatus.COMPLETED,
                WAIT);

        assertThat(audit.findByRunIdOrderByTimestampAsc(id))
                .extracting(AuditRecord::getEventType)
                .contains(
                        "PLAN_APPROVED",
                        "RESULT_APPROVED",
                        "LLM_REQUEST",
                        "TASK_FINISHED");
    }

    @Test
    void blockingAmbiguityPausesForAHumanAnswer() {
        llm.put("analyst", ANALYSIS_BLOCKING);

        String id = engine.start(
                "Improve the thing",
                "test").getId();

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_CLARIFICATION,
                WAIT);

        approvals.submitClarifications(
                id,
                Map.of("Which service?", "the billing service"));

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_PLAN_APPROVAL,
                WAIT);

        assertThat(artifacts.findByRunIdOrderByCreatedAtAsc(id))
                .extracting(Artifact::getType)
                .contains(ArtifactType.CLARIFICATION_ANSWERS);
    }

    @Test
    void removingATaskRewiresDependenciesAndSkipsItsWork() {
        String id = engine.start(
                "Build a tiny service",
                "test").getId();

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_PLAN_APPROVAL,
                WAIT);

        approvals.approvePlan(id, Set.of("risk"));

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_REVIEW,
                WAIT);

        assertThat(nodes.findByRunIdOrderById(id))
                .extracting(TaskNode::getTaskKey)
                .containsExactly(
                        "arch",
                        "summary");

        assertThat(
                nodes.findByRunIdAndTaskKey(id, "summary")
                        .orElseThrow()
                        .getDependsOn())
                .containsExactly("arch");

        assertThat(llm.calls)
                .doesNotContain("risk");
    }

    @Test
    void failedTaskSkipsDependentsAndResumeContinuesFromTheFailure() {
        llm.remove("risk");

        String id = engine.start(
                "Build a tiny service",
                "test").getId();

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_PLAN_APPROVAL,
                WAIT);

        approvals.approvePlan(id, Set.of());

        RunAwaiter.await(
                runs,
                id,
                RunStatus.FAILED,
                WAIT);

        assertThat(
                nodes.findByRunIdAndTaskKey(id, "arch")
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(TaskStatus.SUCCEEDED);

        assertThat(
                nodes.findByRunIdAndTaskKey(id, "risk")
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(TaskStatus.FAILED);

        assertThat(
                nodes.findByRunIdAndTaskKey(id, "summary")
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(TaskStatus.SKIPPED);

        llm.put("risk", RISK_OK);

        engine.resume(id);

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_REVIEW,
                WAIT);

        assertThat(nodes.findByRunIdOrderById(id))
                .allMatch(n -> n.getStatus() == TaskStatus.SUCCEEDED);

        assertThat(
                llm.calls.stream()
                        .filter("arch"::equals)
                        .count())
                .isEqualTo(1);
    }

    @Test
    void humanGatesCannotBeSkippedOrRepeated() {
        String id = engine.start(
                "Build a tiny service",
                "test").getId();

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_PLAN_APPROVAL,
                WAIT);

        assertThatThrownBy(
                () -> approvals.approveResult(id))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(
                () -> approvals.submitClarifications(id, Map.of()))
                .isInstanceOf(IllegalStateException.class);

        approvals.approvePlan(id, Set.of());

        assertThatThrownBy(
                () -> approvals.approvePlan(id, Set.of()))
                .isInstanceOf(IllegalStateException.class);

        RunAwaiter.await(
                runs,
                id,
                RunStatus.AWAITING_REVIEW,
                WAIT);
    }
}
