package com.example.agentic.domain.repo;

import com.example.agentic.domain.Artifact;
import com.example.agentic.domain.ArtifactType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtifactRepository extends JpaRepository<Artifact, Long> {
    List<Artifact> findByRunIdOrderByCreatedAtAsc(String runId);
    List<Artifact> findByRunIdAndPathOrderByRevisionDesc(String runId, String path);
    List<Artifact> findByRunIdAndTypeOrderByIdDesc(String runId, ArtifactType type);
}