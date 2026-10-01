package com.example.agentic.api.dto;

import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import java.time.Instant;

public record ArtifactResponse(
        Long id,
        String taskKey,
        ArtifactType type,
        String path,
        int revision,
        Instant createdAt,
        String content) {

    public static ArtifactResponse metadata(Artifact artifact) {
        return new ArtifactResponse(
                artifact.getId(),
                artifact.getTaskKey(),
                artifact.getType(),
                artifact.getPath(),
                artifact.getRevision(),
                artifact.getCreatedAt(),
                null);
    }

    public static ArtifactResponse from(Artifact artifact) {
        return new ArtifactResponse(
                artifact.getId(),
                artifact.getTaskKey(),
                artifact.getType(),
                artifact.getPath(),
                artifact.getRevision(),
                artifact.getCreatedAt(),
                artifact.getContent());
    }
}
