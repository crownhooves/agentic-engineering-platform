package com.example.agentic.api;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.orchestrator.RunLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RunEventStreamServiceTest {

    @Test
    void rejectsUnknownRunImmediately() {
        RunLifecycle lifecycle =
                mock(RunLifecycle.class);

        given(lifecycle.get("missing"))
                .willThrow(
                        new NoSuchElementException(
                                "Run not found: missing"
                        )
                );

        RunEventStreamService service =
                new RunEventStreamService(lifecycle);

        assertThrows(
                NoSuchElementException.class,
                () -> service.open("missing")
        );
    }

    @Test
    void createsEmitterForExistingRun() {
        RunLifecycle lifecycle =
                mock(RunLifecycle.class);

        Run run = run(RunStatus.EXECUTING);

        given(lifecycle.get("run-1"))
                .willReturn(run);

        given(lifecycle.tasks("run-1"))
                .willReturn(List.of());

        RunEventStreamService service =
                new RunEventStreamService(lifecycle);

        SseEmitter emitter =
                service.open("run-1");

        try {
            assertThat(emitter)
                    .isNotNull();
        } finally {
            emitter.complete();
        }
    }

    @Test
    void allowsFailedRunToBeObservedForResume() {
        RunLifecycle lifecycle =
                mock(RunLifecycle.class);

        Run run = run(RunStatus.FAILED);

        given(lifecycle.get("run-1"))
                .willReturn(run);

        given(lifecycle.tasks("run-1"))
                .willReturn(List.of());

        RunEventStreamService service =
                new RunEventStreamService(lifecycle);

        SseEmitter emitter =
                service.open("run-1");

        try {
            assertThat(emitter)
                    .isNotNull();
        } finally {
            emitter.complete();
        }
    }

    private Run run(RunStatus status) {
        Run run = new Run(
                "Test requirement",
                "demo"
        );

        run.setStatus(status);

        return run;
    }
}