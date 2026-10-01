package com.example.agentic.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "runs")
public class Run {

    @Id
    private String id;

    @Lob
    @Column(nullable = false)
    private String requirement;

    /** Mock recording set / preset: url-shortener | brownfield | ambiguous | custom. */
    private String scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(length = 2000)
    private String failureReason;

    private Instant createdAt;
    private Instant updatedAt;

    /** Optimistic locking: concurrent state transitions fail loudly instead of overwriting. */
    @Version
    private Long version;

    protected Run() {}

    public Run(String requirement, String scenario) {
        this.id = UUID.randomUUID().toString();
        this.requirement = requirement;
        this.scenario = scenario;
        this.status = RunStatus.CREATED;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getRequirement() { return requirement; }
    public String getScenario() { return scenario; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String r) {
        this.failureReason = r == null ? null : r.substring(0, Math.min(r.length(), 2000));
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}