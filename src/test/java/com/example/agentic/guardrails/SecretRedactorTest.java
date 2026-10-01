package com.example.agentic.guardrails;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SecretRedactorTest {

    private final SecretRedactor redactor = new SecretRedactor();

    @Test
    void redactsAwsAccessKeys() {
        String out = redactor.redact("key=AKIAIOSFODNN7EXAMPLE used");
        assertThat(out).doesNotContain("AKIAIOSFODNN7EXAMPLE").contains("[REDACTED]");
    }

    @Test
    void redactsQuotedAssignmentButKeepsKeyName() {
        String out = redactor.redact("String password = \"hunter2hunter2\";");
        assertThat(out).contains("password").contains("[REDACTED]").doesNotContain("hunter2");
    }

    @Test
    void redactsConfigStyleLines() {
        String out = redactor.redact("spring.datasource.password=s3cretValue99\nother=fine");
        assertThat(out).doesNotContain("s3cretValue99").contains("other=fine");
    }

    @Test
    void redactsPrivateKeyBlocks() {
        String out = redactor.redact("x\n-----BEGIN RSA PRIVATE KEY-----\nMIIabc\n-----END RSA PRIVATE KEY-----\ny");
        assertThat(out).doesNotContain("MIIabc").contains("x").contains("y");
    }

    @Test
    void leavesOrdinaryCodeAlone() {
        String code = "String password = readFromVault();\nint tokenCount = 5;\nString risk = \"assessment\";";
        assertThat(redactor.redact(code)).isEqualTo(code);
    }
}