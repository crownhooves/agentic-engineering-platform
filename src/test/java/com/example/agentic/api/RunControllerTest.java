package com.example.agentic.api;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import com.example.agentic.orchestrator.ApprovalService;
import com.example.agentic.orchestrator.RunLifecycle;
import com.example.agentic.orchestrator.WorkflowEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RunController.class)
class RunControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkflowEngine workflowEngine;

    @MockitoBean
    private RunLifecycle lifecycle;

    @MockitoBean
    private ApprovalService approvalService;

    @Test
    void startReturnsAccepted() throws Exception {
        Run run = new Run(
                "Build a REST API",
                "spring-boot"
        );
        run.setStatus(RunStatus.ANALYZING);

        given(workflowEngine.start(
                "Build a REST API",
                "spring-boot"
        )).willReturn(run);

        mvc.perform(
                        post("/api/runs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "requirement": "Build a REST API",
                                          "scenario": "spring-boot"
                                        }
                                        """)
                )
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id")
                        .value(run.getId()))
                .andExpect(jsonPath("$.status")
                        .value("ANALYZING"))
                .andExpect(jsonPath("$.requirement")
                        .value("Build a REST API"))
                .andExpect(jsonPath("$.scenario")
                        .value("spring-boot"));
    }

    @Test
    void startRejectsBlankRequirement() throws Exception {
        mvc.perform(
                        post("/api/runs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "requirement": "",
                                          "scenario": "demo"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verify(workflowEngine, never())
                .start(anyString(), any());
    }

    @Test
    void startRejectsMissingRequirement() throws Exception {
        mvc.perform(
                        post("/api/runs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "scenario": "demo"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verify(workflowEngine, never())
                .start(anyString(), any());
    }

    @Test
    void getReturnsRunAndTasks() throws Exception {
        Run run = new Run(
                "Test requirement",
                "demo"
        );
        run.setStatus(RunStatus.EXECUTING);

        given(lifecycle.get("run-1"))
                .willReturn(run);

        given(lifecycle.tasks("run-1"))
                .willReturn(List.of());

        mvc.perform(
                        get("/api/runs/run-1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.id")
                        .value(run.getId()))
                .andExpect(jsonPath("$.run.status")
                        .value("EXECUTING"))
                .andExpect(jsonPath("$.run.requirement")
                        .value("Test requirement"))
                .andExpect(jsonPath("$.tasks")
                        .isArray());
    }

    @Test
    void unknownRunReturnsNotFound() throws Exception {
        given(lifecycle.get("missing"))
                .willThrow(new NoSuchElementException(
                        "Run not found: missing"
                ));

        mvc.perform(
                        get("/api/runs/missing")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Run not found: missing"))
                .andExpect(jsonPath("$.timestamp")
                        .exists());
    }

    @Test
    void resumeReturnsAccepted() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/resume")
                )
                .andExpect(status().isAccepted());

        verify(workflowEngine)
                .resume("run-1");
    }

    @Test
    void submitClarificationsReturnsAccepted() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/clarifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "answers": {
                                            "database": "PostgreSQL",
                                            "authentication": "JWT"
                                          }
                                        }
                                        """)
                )
                .andExpect(status().isAccepted());

        verify(approvalService).submitClarifications(
                "run-1",
                Map.of(
                        "database", "PostgreSQL",
                        "authentication", "JWT"
                )
        );
    }

    @Test
    void approvePlanReturnsAccepted() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/plan/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "removedTaskKeys": [
                                            "documentation",
                                            "tests"
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isAccepted());

        verify(approvalService).approvePlan(
                "run-1",
                Set.of(
                        "documentation",
                        "tests"
                )
        );
    }

    @Test
    void approvePlanWithoutBodyUsesEmptySet() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/plan/approve")
                )
                .andExpect(status().isAccepted());

        verify(approvalService).approvePlan(
                "run-1",
                Set.of()
        );
    }

    @Test
    void approveResultReturnsNoContent() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/result/approve")
                )
                .andExpect(status().isNoContent());

        verify(approvalService)
                .approveResult("run-1");
    }

    @Test
    void rejectResultReturnsNoContent() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/result/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason": "Needs another review"
                                        }
                                        """)
                )
                .andExpect(status().isNoContent());

        verify(approvalService)
                .rejectResult(
                        "run-1",
                        "Needs another review"
                );
    }

    @Test
    void rejectResultWithoutBodyPassesNullReason() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/result/reject")
                )
                .andExpect(status().isNoContent());

        verify(approvalService)
                .rejectResult("run-1", null);
    }

    @Test
    void cancelReturnsNoContent() throws Exception {
        mvc.perform(
                        post("/api/runs/run-1/cancel")
                )
                .andExpect(status().isNoContent());

        verify(approvalService)
                .cancel("run-1");
    }
}