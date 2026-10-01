package com.example.agentic.api;

import com.example.agentic.api.dto.ClarificationRequest;
import com.example.agentic.api.dto.PlanApprovalRequest;
import com.example.agentic.api.dto.ResultReviewRequest;
import com.example.agentic.api.dto.RunResponse;
import com.example.agentic.api.dto.RunStatusResponse;
import com.example.agentic.api.dto.StartRunRequest;
import com.example.agentic.domain.Run;
import com.example.agentic.domain.TaskNode;
import com.example.agentic.orchestrator.ApprovalService;
import com.example.agentic.orchestrator.RunLifecycle;
import com.example.agentic.orchestrator.WorkflowEngine;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final WorkflowEngine engine;
    private final RunLifecycle lifecycle;
    private final ApprovalService approvalService;

    public RunController(
            WorkflowEngine engine,
            RunLifecycle lifecycle,
            ApprovalService approvalService) {
        this.engine = engine;
        this.lifecycle = lifecycle;
        this.approvalService = approvalService;
    }

    @PostMapping
    public ResponseEntity<RunResponse> start(
            @Valid @RequestBody StartRunRequest request) {

        Run run = engine.start(request.requirement(), request.scenario());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(RunResponse.from(run));
    }

    @GetMapping
    public List<RunStatusResponse> getRuns() {
        return lifecycle.getAll().stream()
                .map(run -> RunStatusResponse.from(
                        run,
                        lifecycle.tasks(run.getId())))
                .toList();
    }

    @GetMapping("/{runId}")
    public RunStatusResponse getStatus(@PathVariable String runId) {
        Run run = lifecycle.get(runId);
        List<TaskNode> tasks = lifecycle.tasks(runId);

        return RunStatusResponse.from(run, tasks);
    }

    @PostMapping("/{runId}/clarifications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void submitClarifications(
            @PathVariable String runId,
            @Valid @RequestBody ClarificationRequest request) {

        approvalService.submitClarifications(
                runId,
                request == null || request.answers() == null
                        ? java.util.Map.of()
                        : request.answers());
    }

    @PostMapping("/{runId}/plan/approve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void approvePlan(
            @PathVariable String runId,
            @Valid @RequestBody(required = false) PlanApprovalRequest request) {

        approvalService.approvePlan(
                runId,
                request == null || request.removedTaskKeys() == null
                        ? java.util.Set.of()
                        : request.removedTaskKeys());
    }

    @PostMapping("/{runId}/result/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approveResult(@PathVariable String runId) {
        approvalService.approveResult(runId);
    }

    @PostMapping("/{runId}/result/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectResult(
            @PathVariable String runId,
            @Valid @RequestBody(required = false) ResultReviewRequest request) {

        approvalService.rejectResult(
                runId,
                request == null ? null : request.reason());
    }

    @PostMapping("/{runId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String runId) {
        approvalService.cancel(runId);
    }

    @PostMapping("/{runId}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resume(@PathVariable String runId) {
        engine.resume(runId);
    }
}