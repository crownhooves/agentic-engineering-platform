package com.example.agentic.domain.repo;

import com.example.agentic.domain.TaskNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskNodeRepository extends JpaRepository<TaskNode, Long> {
    List<TaskNode> findByRunIdOrderById(String runId);
    Optional<TaskNode> findByRunIdAndTaskKey(String runId, String taskKey);
    void deleteByRunId(String runId);   // used when a plan is regenerated/edited
}