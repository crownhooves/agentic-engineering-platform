package com.example.agentic.guardrails;

import com.example.agentic.domain.AuditRecord;
import com.example.agentic.domain.repo.AuditRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Append-only audit trail. Everything is redacted and size-capped before it is stored. */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final int MAX_DETAIL_CHARS = 50_000;

    private final AuditRecordRepository repository;
    private final SecretRedactor redactor;

    public AuditLogger(AuditRecordRepository repository, SecretRedactor redactor) {
        this.repository = repository;
        this.redactor = redactor;
    }

    /** Never throws: a failing audit write is logged loudly but must not kill the run (fail-open). */
    public void log(String runId, String actor, String eventType, String detail) {
        try {
            String safe = redactor.redact(detail == null ? "" : detail);
            if (safe.length() > MAX_DETAIL_CHARS) {
                safe = safe.substring(0, MAX_DETAIL_CHARS)
                        + "\n...[truncated " + (safe.length() - MAX_DETAIL_CHARS) + " chars]";
            }
            repository.save(new AuditRecord(runId, actor, eventType, safe));
        } catch (RuntimeException e) {
            log.error("AUDIT WRITE FAILED run={} actor={} event={}", runId, actor, eventType, e);
        }
    }
}