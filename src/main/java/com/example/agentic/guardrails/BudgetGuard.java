package com.example.agentic.guardrails;

import com.example.agentic.config.BudgetProperties;
import com.example.agentic.llm.TokenUsage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Per-run call and token budgets. The call slot is reserved before the call; tokens are only
 * known afterwards, so the token limit is a soft limit that parallel calls may overshoot slightly.
 * Counters are in memory: a resumed run after a restart starts with a fresh budget.
 */
@Component
public class BudgetGuard {

    public record Snapshot(long tokens, int calls) {}

    private static final class RunBudget {
        final AtomicLong tokens = new AtomicLong();
        final AtomicInteger calls = new AtomicInteger();
    }

    private final BudgetProperties props;
    private final ConcurrentMap<String, RunBudget> budgets = new ConcurrentHashMap<>();

    public BudgetGuard(BudgetProperties props) { this.props = props; }

    public void checkBeforeCall(String runId) {
        RunBudget b = budgets.computeIfAbsent(runId, k -> new RunBudget());
        if (b.tokens.get() >= props.maxTokensPerRun()) {
            throw new BudgetExceededException("Run " + runId + " exhausted its token budget ("
                    + props.maxTokensPerRun() + ")");
        }
        if (b.calls.incrementAndGet() > props.maxLlmCallsPerRun()) {
            b.calls.decrementAndGet();
            throw new BudgetExceededException("Run " + runId + " exhausted its LLM call budget ("
                    + props.maxLlmCallsPerRun() + ")");
        }
    }

    public void recordUsage(String runId, TokenUsage usage) {
        budgets.computeIfAbsent(runId, k -> new RunBudget()).tokens.addAndGet(usage.total());
    }

    public Snapshot snapshot(String runId) {
        RunBudget b = budgets.get(runId);
        return b == null ? new Snapshot(0, 0) : new Snapshot(b.tokens.get(), b.calls.get());
    }

    /** Call when a run reaches a terminal state so the map does not grow forever. */
    public void release(String runId) { budgets.remove(runId); }
}