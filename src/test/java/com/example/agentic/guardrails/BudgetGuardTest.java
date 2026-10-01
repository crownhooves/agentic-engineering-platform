package com.example.agentic.guardrails;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.common.EscalationRequiredException;
import com.example.agentic.config.BudgetProperties;
import com.example.agentic.llm.TokenUsage;
import org.junit.jupiter.api.Test;

class BudgetGuardTest {

    @Test
    void blocksCallsBeyondTheLimitAsAnEscalation() {
        BudgetGuard g = new BudgetGuard(new BudgetProperties(1_000_000, 2));
        g.checkBeforeCall("r1");
        g.checkBeforeCall("r1");
        assertThatThrownBy(() -> g.checkBeforeCall("r1"))
                .isInstanceOf(BudgetExceededException.class)
                .isInstanceOf(EscalationRequiredException.class);
        assertThat(g.snapshot("r1").calls()).isEqualTo(2);       // rejected call was not counted
    }

    @Test
    void blocksOnceTokenBudgetIsSpent() {
        BudgetGuard g = new BudgetGuard(new BudgetProperties(100, 10));
        g.checkBeforeCall("r1");
        g.recordUsage("r1", new TokenUsage(60, 60));
        assertThatThrownBy(() -> g.checkBeforeCall("r1")).isInstanceOf(BudgetExceededException.class);
    }

    @Test
    void budgetsAreIsolatedPerRun() {
        BudgetGuard g = new BudgetGuard(new BudgetProperties(1_000_000, 1));
        g.checkBeforeCall("r1");
        assertThatCode(() -> g.checkBeforeCall("r2")).doesNotThrowAnyException();
    }
}