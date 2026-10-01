package com.example.agentic.llm;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.agents.AgentType;
import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.common.NonRetryableException;
import com.example.agentic.config.BudgetProperties;
import com.example.agentic.config.LlmProperties;
import com.example.agentic.domain.AuditRecord;
import com.example.agentic.guardrails.*;
import com.example.agentic.observability.AgentMetrics;
import com.example.agentic.testsupport.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {

    private static final String GOOD = "{\"name\":\"ok\",\"count\":1}";

    private final InMemoryAuditRepository auditRepo = new InMemoryAuditRepository();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private LlmGateway gateway(ScriptedLlmClient client, BudgetProperties budget) {
        SecretRedactor redactor = new SecretRedactor();
        return new LlmGateway(client, StructuredOutputParser.withDefaults(), new BudgetGuard(budget),
                redactor, new AuditLogger(auditRepo, redactor), new AgentMetrics(registry),
                new LlmProperties(null, null, 2));
    }

    private LlmGateway gateway(ScriptedLlmClient client) {
        return gateway(client, new BudgetProperties(1_000_000L, 100));
    }

    private static LlmRequest request(String userPrompt) {
        return new LlmRequest("run-1", "url-shortener", AgentType.PLANNER, "planner",
                "system", userPrompt, true);
    }

    @Test
    void returnsParsedOutputOnFirstTry() {
        ScriptedLlmClient client = new ScriptedLlmClient(false, GOOD);
        Sample s = gateway(client).callJson(request("hi"), Sample.class);
        assertThat(s.name()).isEqualTo("ok");
        assertThat(client.requests).hasSize(1);
    }

    @Test
    void repromptsWithTheValidationErrorWhenOutputIsMalformed() {
        ScriptedLlmClient client = new ScriptedLlmClient(false, "sorry, I cannot do that", GOOD);
        Sample s = gateway(client).callJson(request("original user prompt"), Sample.class);

        assertThat(s.name()).isEqualTo("ok");
        assertThat(client.requests).hasSize(2);
        assertThat(client.requests.get(1).callKey()).isEqualTo("planner-reprompt-1");
        assertThat(client.requests.get(1).userPrompt())
                .contains("original user prompt").contains("rejected");
    }

    @Test
    void liveModelFailureAfterRepairsIsRetryable() {
        ScriptedLlmClient client = new ScriptedLlmClient(false, "bad1", "bad2", "bad3");
        assertThatThrownBy(() -> gateway(client).callJson(request("hi"), Sample.class))
                .isInstanceOf(InvalidOutputException.class)
                .isNotInstanceOf(NonRetryableException.class);
        assertThat(client.requests).hasSize(3);                  // 1 call + 2 repairs
    }

    @Test
    void mockRecordingFailureIsNonRetryable() {
        ScriptedLlmClient client = new ScriptedLlmClient(true, "bad1", "bad2", "bad3");
        assertThatThrownBy(() -> gateway(client).callJson(request("hi"), Sample.class))
                .isInstanceOf(NonRetryableException.class);
    }

    @Test
    void budgetExhaustionStopsFurtherCallsAndEscalates() {
        ScriptedLlmClient client = new ScriptedLlmClient(false, "bad", GOOD);
        LlmGateway gw = gateway(client, new BudgetProperties(1_000_000L, 1));
        assertThatThrownBy(() -> gw.callJson(request("hi"), Sample.class))
                .isInstanceOf(BudgetExceededException.class);
        assertThat(client.requests).hasSize(1);
        assertThat(auditRepo.records).extracting(AuditRecord::getEventType).contains("GUARDRAIL_BLOCKED");
    }

    @Test
    void secretsNeverReachTheModelOrTheAuditLog() {
        ScriptedLlmClient client = new ScriptedLlmClient(false, GOOD);
        gateway(client).callJson(request("config: String password = \"hunter2hunter2\";"), Sample.class);

        assertThat(client.requests.get(0).userPrompt()).doesNotContain("hunter2");
        assertThat(auditRepo.records).allSatisfy(r -> assertThat(r.getDetail()).doesNotContain("hunter2"));
    }

    @Test
    void recordsAuditEventsAndMetrics() {
        ScriptedLlmClient client = new ScriptedLlmClient(false, GOOD);
        gateway(client).callJson(request("hi"), Sample.class);

        assertThat(auditRepo.records).extracting(AuditRecord::getEventType)
                .contains("LLM_REQUEST", "LLM_RESPONSE");
        Counter calls = registry.find("agent.llm.calls").tag("outcome", "success").counter();
        assertThat(calls).isNotNull();
        assertThat(calls.count()).isEqualTo(1.0);
        double tokens = registry.find("agent.llm.tokens").counters().stream().mapToDouble(Counter::count).sum();
        assertThat(tokens).isEqualTo(150.0);                     // 100 prompt + 50 completion
    }
}