package com.example.agentic.agents.dto;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.llm.Validatable;
import java.util.List;

public record RiskOutput(List<Risk> risks, List<FailureScenario> failureScenarios,
                         List<String> validationStrategy, List<String> tradeoffs) implements Validatable {

    public record Risk(String id, String description, String likelihood, String impact, String mitigation) {}

    public record FailureScenario(String scenario, String detection, String mitigation) {}

    public RiskOutput {
        risks = Lists.nn(risks);
        failureScenarios = Lists.nn(failureScenarios);
        validationStrategy = Lists.nn(validationStrategy);
        tradeoffs = Lists.nn(tradeoffs);
    }

    @Override
    public void validate() {
        if (risks.isEmpty()) throw new InvalidOutputException("'risks' must not be empty");
        if (failureScenarios.isEmpty()) throw new InvalidOutputException("'failureScenarios' must not be empty");
        if (validationStrategy.isEmpty()) throw new InvalidOutputException("'validationStrategy' must not be empty");
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder("# Risks, failure scenarios and validation\n\n## Risk register\n\n")
                .append("| ID | Risk | Likelihood | Impact | Mitigation |\n|---|---|---|---|---|\n");
        for (Risk r : risks) {
            sb.append("| ").append(r.id()).append(" | ").append(r.description()).append(" | ")
                    .append(r.likelihood()).append(" | ").append(r.impact()).append(" | ")
                    .append(r.mitigation()).append(" |\n");
        }
        sb.append("\n## Failure scenarios\n");
        for (FailureScenario f : failureScenarios) {
            sb.append("- **").append(f.scenario()).append("** - detected by: ").append(f.detection())
                    .append("; mitigation: ").append(f.mitigation()).append("\n");
        }
        sb.append("\n## Validation strategy\n");
        validationStrategy.forEach(v -> sb.append("- ").append(v).append("\n"));
        if (!tradeoffs.isEmpty()) {
            sb.append("\n## Trade-offs\n");
            tradeoffs.forEach(t -> sb.append("- ").append(t).append("\n"));
        }
        return sb.toString();
    }
}