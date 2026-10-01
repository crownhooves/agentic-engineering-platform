package com.example.agentic.domain.repo;

import com.example.agentic.domain.Run;
import com.example.agentic.domain.RunStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, String> {
    List<Run> findByStatus(RunStatus status);
    List<Run> findAllByOrderByCreatedAtDesc();
}