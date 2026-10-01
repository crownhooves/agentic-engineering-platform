package com.example.agentic.observability;

import com.example.agentic.agents.AgentType;
import com.example.agentic.llm.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;

/** Tags are agent / model / outcome only. Run IDs are never used as labels (cardinality). */
public class AgentMetrics {

    private final MeterRegistry registry;

    public AgentMetrics(MeterRegistry registry) { this.registry = registry; }

    public void recordLlmCall(AgentType agent, String model, TokenUsage usage,
                              Duration latency, String outcome) {
        String a = agent.name().toLowerCase(Locale.ROOT);
        String m = model == null ? "unknown" : model;

        Counter.builder("agent.llm.calls")
                .tags("agent", a, "model", m, "outcome", outcome)
                .register(registry).increment();
        Timer.builder("agent.llm.latency")
                .tags("agent", a, "model", m, "outcome", outcome)
                .publishPercentiles(0.5, 0.95)
                .register(registry).record(latency);
        Counter.builder("agent.llm.tokens")
                .tags("agent", a, "model", m, "type", "prompt")
                .register(registry).increment(usage.promptTokens());
        Counter.builder("agent.llm.tokens")
                .tags("agent", a, "model", m, "type", "completion")
                .register(registry).increment(usage.completionTokens());
    }

    public void recordInvalidOutput(AgentType agent) {
        Counter.builder("agent.output.invalid")
                .tag("agent", agent.name().toLowerCase(Locale.ROOT))
                .register(registry).increment();
    }
}