package com.example.agentic.testsupport;

import com.example.agentic.domain.AuditRecord;
import com.example.agentic.domain.repo.AuditRecordRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryAuditRepository implements AuditRecordRepository {

    public final List<AuditRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public AuditRecord save(AuditRecord record) {
        records.add(record);
        return record;
    }

    @Override
    public List<AuditRecord> findByRunIdOrderByTimestampAsc(String runId) {
        return records.stream().filter(r -> r.getRunId().equals(runId)).toList();
    }
}