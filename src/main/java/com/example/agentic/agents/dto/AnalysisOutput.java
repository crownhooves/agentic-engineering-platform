package com.example.agentic.agents.dto;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.llm.Validatable;
import java.util.List;

public record AnalysisOutput(String normalizedRequirement, WorkType workType,
                             List<String> functionalRequirements, List<String> nonFunctionalRequirements,
                             List<Ambiguity> ambiguities, List<String> assumptions,
                             List<String> outOfScope) implements Validatable {

    /** blocking=true means the run pauses for a human answer; otherwise assumedAnswer is used. */
    public record Ambiguity(String question, String impact, String assumedAnswer, boolean blocking) {}

    public AnalysisOutput {
        functionalRequirements = Lists.nn(functionalRequirements);
        nonFunctionalRequirements = Lists.nn(nonFunctionalRequirements);
        ambiguities = Lists.nn(ambiguities);
        assumptions = Lists.nn(assumptions);
        outOfScope = Lists.nn(outOfScope);
    }

    @Override
    public void validate() {
        if (normalizedRequirement == null || normalizedRequirement.isBlank())
            throw new InvalidOutputException("field 'normalizedRequirement' is required");
        if (workType == null)
            throw new InvalidOutputException(
                    "field 'workType' must be one of GREENFIELD, BROWNFIELD, BUGFIX, REFACTOR, TESTS_AND_DOCS");
        if (functionalRequirements.isEmpty())
            throw new InvalidOutputException("'functionalRequirements' must not be empty");
    }

    public List<Ambiguity> blockingQuestions() {
        return ambiguities.stream().filter(Ambiguity::blocking).toList();
    }
}