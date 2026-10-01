package com.example.agentic.domain;

import com.example.agentic.agents.AgentType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "task_nodes", uniqueConstraints = @UniqueConstraint(columnNames = {"runId", "taskKey"}))
public class TaskNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String runId;

    @Column(nullable = false)
    private String taskKey;

    private String title;

    @Lob
    private String description;

    @Enumerated(EnumType.STRING)
    private AgentType agent;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_dependencies", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "depends_on")
    private Set<String> dependsOn = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;

    private int attempts;

    @Column(length = 2000)
    private String lastError;

    private Instant startedAt;
    private Instant finishedAt;

    protected TaskNode() {}

    public TaskNode(String runId, String taskKey, String title, String description,
                    AgentType agent, Set<String> dependsOn) {
        this.runId = runId;
        this.taskKey = taskKey;
        this.title = title;
        this.description = description;
        this.agent = agent;
        this.dependsOn = new LinkedHashSet<>(dependsOn);
    }

    public Long getId() { return id; }
    public String getRunId() { return runId; }
    public String getTaskKey() { return taskKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public AgentType getAgent() { return agent; }
    public Set<String> getDependsOn() { return dependsOn; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String e) {
        this.lastError = e == null ? null : e.substring(0, Math.min(e.length(), 2000));
    }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant t) { this.startedAt = t; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant t) { this.finishedAt = t; }
}