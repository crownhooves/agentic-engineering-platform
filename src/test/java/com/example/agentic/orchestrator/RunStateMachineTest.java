package com.example.agentic.orchestrator;

import static com.example.agentic.domain.RunStatus.*;
import static org.assertj.core.api.Assertions.*;

import com.example.agentic.domain.RunStatus;
import org.junit.jupiter.api.Test;

class RunStateMachineTest {

    @Test
    void happyPathIsLegal() {
        RunStatus[] path = {CREATED, ANALYZING, PLANNING, AWAITING_PLAN_APPROVAL,
                EXECUTING, AWAITING_REVIEW, COMPLETED};
        for (int i = 0; i < path.length - 1; i++) {
            assertThat(RunStateMachine.canTransition(path[i], path[i + 1]))
                    .as(path[i] + " -> " + path[i + 1]).isTrue();
        }
    }

    @Test
    void cannotSkipHumanApprovalGate() {
        assertThat(RunStateMachine.canTransition(PLANNING, EXECUTING)).isFalse();
        assertThat(RunStateMachine.canTransition(EXECUTING, COMPLETED)).isFalse();
        assertThatThrownBy(() -> RunStateMachine.require(CREATED, EXECUTING))
                .isInstanceOf(RunStateMachine.IllegalRunTransitionException.class);
    }

    @Test
    void failedRunsCanBeResumed() {
        assertThat(RunStateMachine.canTransition(FAILED, EXECUTING)).isTrue();
    }

    @Test
    void terminalStatesHaveNoExits() {
        for (RunStatus s : new RunStatus[]{COMPLETED, REJECTED, CANCELLED}) {
            assertThat(RunStateMachine.isTerminal(s)).isTrue();
            assertThat(RunStateMachine.allowedFrom(s)).isEmpty();
        }
    }

    @Test
    void everyStatusIsDefined() {
        for (RunStatus s : RunStatus.values()) {
            assertThatCode(() -> RunStateMachine.allowedFrom(s)).doesNotThrowAnyException();
        }
    }
}