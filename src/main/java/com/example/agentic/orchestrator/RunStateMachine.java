package com.example.agentic.orchestrator;

import com.example.agentic.domain.RunStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.example.agentic.domain.RunStatus.*;

/** Single source of truth for legal run state transitions. */
public final class RunStateMachine {

    public static class IllegalRunTransitionException extends IllegalStateException {
        public IllegalRunTransitionException(RunStatus from, RunStatus to) {
            super("Illegal run transition " + from + " -> " + to);
        }
    }

    private static final Map<RunStatus, Set<RunStatus>> ALLOWED = new EnumMap<>(RunStatus.class);

    static {
        ALLOWED.put(CREATED, EnumSet.of(ANALYZING, CANCELLED));
        ALLOWED.put(ANALYZING, EnumSet.of(AWAITING_CLARIFICATION, PLANNING, FAILED));
        ALLOWED.put(AWAITING_CLARIFICATION, EnumSet.of(PLANNING, CANCELLED));
        ALLOWED.put(PLANNING, EnumSet.of(AWAITING_PLAN_APPROVAL, FAILED));
        ALLOWED.put(AWAITING_PLAN_APPROVAL, EnumSet.of(EXECUTING, PLANNING, CANCELLED));
        ALLOWED.put(EXECUTING, EnumSet.of(AWAITING_REVIEW, FAILED, CANCELLED));
        ALLOWED.put(AWAITING_REVIEW, EnumSet.of(COMPLETED, EXECUTING, REJECTED));
        // FAILED is resumable: retry analysis/planning or resume execution from the failed task.
        ALLOWED.put(FAILED, EnumSet.of(ANALYZING, PLANNING, EXECUTING, CANCELLED));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(RunStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(RunStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(RunStatus.class));
    }

    private RunStateMachine() {}

    public static Set<RunStatus> allowedFrom(RunStatus from) { return Set.copyOf(ALLOWED.get(from)); }

    public static boolean canTransition(RunStatus from, RunStatus to) { return ALLOWED.get(from).contains(to); }

    public static void require(RunStatus from, RunStatus to) {
        if (!canTransition(from, to)) throw new IllegalRunTransitionException(from, to);
    }

    public static boolean isTerminal(RunStatus s) { return ALLOWED.get(s).isEmpty(); }
}