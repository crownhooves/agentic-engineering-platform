package com.example.agentic.agents.dto;

import com.example.agentic.agents.AgentType;
import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.llm.Validatable;
import com.example.agentic.orchestrator.InvalidGraphException;
import com.example.agentic.orchestrator.TaskGraph;
import com.example.agentic.orchestrator.TaskSpec;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record PlanOutput(String rationale, List<PlannedTask> tasks) implements Validatable {

    public record PlannedTask(String key, String title, String description, AgentType agent,
                              List<String> dependsOn, String risk, Integer estimateHours) {
        public PlannedTask { dependsOn = Lists.nn(dependsOn); }
    }

    private static final Set<AgentType> EXECUTABLE = EnumSet.of(AgentType.CODEBASE_ANALYST,
            AgentType.ARCHITECT, AgentType.CODER, AgentType.TEST_WRITER, AgentType.REVIEWER,
            AgentType.RISK, AgentType.SUMMARIZER);

    public PlanOutput { tasks = Lists.nn(tasks); }

    @Override
    public void validate() {
        if (tasks.isEmpty()) throw new InvalidOutputException("plan must contain at least one task");
        for (PlannedTask t : tasks) {
            if (t.key() == null || t.key().isBlank())
                throw new InvalidOutputException("every task needs a non-blank 'key'");
            if (t.agent() == null)
                throw new InvalidOutputException("task '" + t.key() + "' has no valid 'agent'; allowed: " + EXECUTABLE);
            if (!EXECUTABLE.contains(t.agent()))
                throw new InvalidOutputException("task '" + t.key() + "' uses agent " + t.agent()
                        + " which cannot execute tasks; allowed: " + EXECUTABLE);
        }
        try {
            graph();
        } catch (InvalidGraphException e) {
            throw new InvalidOutputException("Invalid task graph: " + e.getMessage(), e);
        }
    }

    public TaskGraph graph() {
        return new TaskGraph(tasks.stream()
                .map(t -> new TaskSpec(t.key(), t.title(), t.agent(), Set.copyOf(t.dependsOn())))
                .toList());
    }
}