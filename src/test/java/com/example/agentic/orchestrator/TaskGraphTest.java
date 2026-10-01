package com.example.agentic.orchestrator;

import static org.assertj.core.api.Assertions.*;

import com.example.agentic.agents.AgentType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskGraphTest {

    static TaskSpec t(String key, String... deps) {
        return new TaskSpec(key, key, AgentType.CODER, Set.of(deps));
    }

    @Test
    void topologicalOrderRespectsDependencies() {
        TaskGraph g = new TaskGraph(List.of(t("D", "B", "C"), t("B", "A"), t("C", "A"), t("A")));
        List<String> order = g.topologicalOrder().stream().map(TaskSpec::key).toList();
        assertThat(order.indexOf("A")).isLessThan(order.indexOf("B"));
        assertThat(order.indexOf("A")).isLessThan(order.indexOf("C"));
        assertThat(order.indexOf("B")).isLessThan(order.indexOf("D"));
        assertThat(order.indexOf("C")).isLessThan(order.indexOf("D"));
    }

    @Test
    void detectsCycle() {
        assertThatThrownBy(() -> new TaskGraph(List.of(t("A", "C"), t("B", "A"), t("C", "B"))))
                .isInstanceOf(InvalidGraphException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void rejectsSelfDependency() {
        assertThatThrownBy(() -> new TaskGraph(List.of(t("A", "A"))))
                .isInstanceOf(InvalidGraphException.class).hasMessageContaining("itself");
    }

    @Test
    void rejectsUnknownDependency() {
        assertThatThrownBy(() -> new TaskGraph(List.of(t("A", "X"))))
                .isInstanceOf(InvalidGraphException.class).hasMessageContaining("unknown");
    }

    @Test
    void rejectsDuplicateKeys() {
        assertThatThrownBy(() -> new TaskGraph(List.of(t("A"), t("A"))))
                .isInstanceOf(InvalidGraphException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void computesTransitiveDependents() {
        TaskGraph g = new TaskGraph(List.of(t("A"), t("B", "A"), t("C", "B"), t("D")));
        assertThat(g.transitiveDependents("A")).containsExactlyInAnyOrder("B", "C");
        assertThat(g.transitiveDependents("D")).isEmpty();
    }

    @Test
    void computesLevels() {
        TaskGraph g = new TaskGraph(List.of(t("A"), t("B", "A"), t("C", "A"), t("D", "B", "C")));
        assertThat(g.levels()).containsEntry("A", 0).containsEntry("B", 1)
                .containsEntry("C", 1).containsEntry("D", 2);
    }
}