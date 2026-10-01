package com.example.agentic.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Append-only: @Immutable + a repository that exposes no update/delete. */
@Entity
@Immutable
@Table(name = "audit_records")
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String runId;

    @Column(updatable = false)
    private Instant timestamp;

    /** e.g. planner, coder, human, system */
    @Column(updatable = false)
    private String actor;

    /** e.g. LLM_CALL, TASK_STARTED, PLAN_APPROVED, GUARDRAIL_BLOCKED */
    @Column(updatable = false)
    private String eventType;

    @Lob
    @Column(updatable = false)
    private String detail;

    protected AuditRecord() {}

    public AuditRecord(String runId, String actor, String eventType, String detail) {
        this.runId = runId;
        this.actor = actor;
        this.eventType = eventType;
        this.detail = detail;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public String getRunId() { return runId; }
    public Instant getTimestamp() { return timestamp; }
    public String getActor() { return actor; }
    public String getEventType() { return eventType; }
    public String getDetail() { return detail; }
}