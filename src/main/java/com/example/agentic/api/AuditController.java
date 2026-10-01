package com.example.agentic.api;

import com.example.agentic.api.dto.AuditResponse;
import com.example.agentic.domain.repo.AuditRecordRepository;
import com.example.agentic.orchestrator.RunLifecycle;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/runs/{runId}/audit")
public class AuditController {

    private final AuditRecordRepository auditRecordRepository;
    private final RunLifecycle lifecycle;

    public AuditController(
            AuditRecordRepository auditRecordRepository,
            RunLifecycle lifecycle) {
        this.auditRecordRepository = auditRecordRepository;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    public List<AuditResponse> getAuditTrail(
            @PathVariable String runId) {

        /*
         * Resolve the run first so a nonexistent run returns
         * the same API-level error semantics as other run APIs.
         */
        lifecycle.get(runId);

        return auditRecordRepository
                .findByRunIdOrderByTimestampAsc(runId)
                .stream()
                .map(AuditResponse::from)
                .toList();
    }
}

