package com.example.agentic.llm;

import com.example.agentic.common.NonRetryableException;
import com.example.agentic.config.LlmProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Replays recorded responses from classpath:mock/{scenario}/{callKey}.json so the demo is
 * deterministic and needs no API key. Token counts are estimated (~4 chars/token).
 */
@Component
@Profile("mock")
public class MockLlmClient implements LlmClient {

    private final LlmProperties props;

    public MockLlmClient(LlmProperties props) { this.props = props; }

    @Override
    public LlmResponse complete(LlmRequest req) {
        String path = "mock/" + req.scenario() + "/" + req.callKey() + ".json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new NonRetryableException("No recorded response for " + path);
        }
        String content;
        try {
            content = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
        long start = System.nanoTime();
        try {
            Thread.sleep(props.mockLatency().toMillis());   // make the timeline look realistic
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NonRetryableException("Interrupted while replaying " + path, e);
        }
        TokenUsage usage = new TokenUsage(
                estimate(req.systemPrompt()) + estimate(req.userPrompt()), estimate(content));
        return new LlmResponse(content, usage, modelName(), Duration.ofNanos(System.nanoTime() - start));
    }

    private static int estimate(String s) { return s == null ? 0 : Math.max(1, s.length() / 4); }

    @Override public String modelName() { return "mock-replay"; }
    @Override public boolean isMock() { return true; }
}