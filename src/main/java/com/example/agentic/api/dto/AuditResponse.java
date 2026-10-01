package com.example.agentic.api.dto;

import com.example.agentic.domain.AuditRecord;
import java.time.Instant;

public record AuditResponse(
        Long id,
        String runId,
        Instant timestamp,
        String actor,
        String eventType,
        String detail) {

    public static AuditResponse from(AuditRecord record) {
        return new AuditResponse(
                record.getId(),
                record.getRunId(),
                record.getTimestamp(),
                record.getActor(),
                record.getEventType(),
                record.getDetail());
    }
}
