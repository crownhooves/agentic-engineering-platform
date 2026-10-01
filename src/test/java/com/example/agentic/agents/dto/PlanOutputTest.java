package com.example.agentic.agents.dto;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.agents.AgentType;
import com.example.agentic.common.InvalidOutputException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanOutputTest {

    private static PlanOutput.PlannedTask t(String key, AgentType agent, String... deps) {
        return new PlanOutput.PlannedTask(key, key, "desc", agent, List.of(deps), "LOW", 1);
    }

    @Test
    void validPlanBuildsAGraph() {
        PlanOutput plan = new PlanOutput("r", List.of(t("arch", AgentType.ARCHITECT),
                t("code", AgentType.CODER, "arch"), t("sum", AgentType.SUMMARIZER, "code")));
        assertThatCode(plan::validate).doesNotThrowAnyException();
        assertThat(plan.graph().size()).isEqualTo(3);
    }

    @Test
    void rejectsCycles() {
        PlanOutput plan = new PlanOutput("r", List.of(t("a", AgentType.CODER, "b"), t("b", AgentType.CODER, "a")));
        assertThatThrownBy(plan::validate).isInstanceOf(InvalidOutputException.class).hasMessageContaining("cycle");
    }

    @Test
    void rejectsUnknownDependencies() {
        PlanOutput plan = new PlanOutput("r", List.of(t("a", AgentType.CODER, "ghost")));
        assertThatThrownBy(plan::validate).isInstanceOf(InvalidOutputException.class).hasMessageContaining("unknown");
    }

    @Test
    void rejectsAgentsThatCannotExecuteTasks() {
        PlanOutput plan = new PlanOutput("r", List.of(t("a", AgentType.PLANNER)));
        assertThatThrownBy(plan::validate).isInstanceOf(InvalidOutputException.class)
                .hasMessageContaining("cannot execute");
    }

    @Test
    void rejectsEmptyPlans() {
        assertThatThrownBy(() -> new PlanOutput("r", List.of()).validate())
                .isInstanceOf(InvalidOutputException.class).hasMessageContaining("at least one");
    }
}