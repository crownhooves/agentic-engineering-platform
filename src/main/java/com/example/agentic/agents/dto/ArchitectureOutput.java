package com.example.agentic.agents.dto;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.llm.Validatable;
import java.util.List;

public record ArchitectureOutput(String overview, List<ComponentSpec> components, List<EntitySpec> dataModel,
                                 String openApiYaml, List<Decision> decisions,
                                 List<String> scalabilityNotes) implements Validatable {

    /** contract = exact signatures other agents code against (they work in parallel). */
    public record ComponentSpec(String name, String responsibility, String contract) {}

    public record EntitySpec(String name, List<String> fields) {
        public EntitySpec { fields = Lists.nn(fields); }
    }

    public record Decision(String decision, String rationale, String tradeoffs) {}

    public ArchitectureOutput {
        components = Lists.nn(components);
        dataModel = Lists.nn(dataModel);
        decisions = Lists.nn(decisions);
        scalabilityNotes = Lists.nn(scalabilityNotes);
    }

    @Override
    public void validate() {
        if (overview == null || overview.isBlank()) throw new InvalidOutputException("field 'overview' is required");
        if (components.isEmpty()) throw new InvalidOutputException("'components' must not be empty");
        if (decisions.isEmpty()) throw new InvalidOutputException("'decisions' must not be empty");
        if (openApiYaml == null || !openApiYaml.contains("openapi") || !openApiYaml.contains("paths"))
            throw new InvalidOutputException(
                    "'openApiYaml' must be a complete OpenAPI document containing 'openapi' and 'paths'");
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder("# Architecture\n\n").append(overview).append("\n\n## Components\n");
        for (ComponentSpec c : components) {
            sb.append("- **").append(c.name()).append("** - ").append(c.responsibility())
                    .append("\n  - Contract: ").append(c.contract()).append("\n");
        }
        sb.append("\n## Data model\n");
        for (EntitySpec e : dataModel) {
            sb.append("- **").append(e.name()).append("**: ").append(String.join(", ", e.fields())).append("\n");
        }
        sb.append("\n## Key decisions and trade-offs\n");
        for (Decision d : decisions) {
            sb.append("- **").append(d.decision()).append("** - ").append(d.rationale())
                    .append(" *Trade-off:* ").append(d.tradeoffs()).append("\n");
        }
        if (!scalabilityNotes.isEmpty()) {
            sb.append("\n## Scalability notes\n");
            scalabilityNotes.forEach(n -> sb.append("- ").append(n).append("\n"));
        }
        return sb.toString();
    }
}