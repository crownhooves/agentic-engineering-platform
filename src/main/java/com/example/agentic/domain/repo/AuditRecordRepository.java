package com.example.agentic.domain.repo;

import com.example.agentic.domain.AuditRecord;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Deliberately NOT a JpaRepository: only append and read are possible. */
public interface AuditRecordRepository extends Repository<AuditRecord, Long> {
    AuditRecord save(AuditRecord record);
    List<AuditRecord> findByRunIdOrderByTimestampAsc(String runId);
}