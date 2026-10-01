package com.example.agentic.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "artifacts")
public class Artifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String runId;

    private String taskKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArtifactType type;

    /** Relative path inside the run workspace, e.g. src/main/java/.../Base62Encoder.java */
    private String path;

    @Lob
    @Column(nullable = false)
    private String content;

    /** Incremented each time the same (runId, path) is rewritten, e.g. by the repair loop. */
    private int revision;

    private Instant createdAt;

    protected Artifact() {}

    public Artifact(String runId, String taskKey, ArtifactType type, String path,
                    String content, int revision) {
        this.runId = runId;
        this.taskKey = taskKey;
        this.type = type;
        this.path = path;
        this.content = content;
        this.revision = revision;
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getRunId() { return runId; }
    public String getTaskKey() { return taskKey; }
    public ArtifactType getType() { return type; }
    public String getPath() { return path; }
    public String getContent() { return content; }
    public int getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
}