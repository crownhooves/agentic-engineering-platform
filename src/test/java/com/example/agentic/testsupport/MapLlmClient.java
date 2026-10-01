package com.example.agentic.testsupport;

import com.example.agentic.common.NonRetryableException;
import com.example.agentic.llm.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Answers by callKey; unknown keys fail like a missing recording. Flagged as mock, so failures are non-retryable. */
public class MapLlmClient implements LlmClient {

    private final Map<String, String> responses = new ConcurrentHashMap<>();
    public final List<String> calls = new CopyOnWriteArrayList<>();

    public MapLlmClient put(String callKey, String content) { responses.put(callKey, content); return this; }
    public void remove(String callKey) { responses.remove(callKey); }
    public void clear() { responses.clear(); calls.clear(); }

    @Override
    public LlmResponse complete(LlmRequest request) {
        calls.add(request.callKey());
        String content = responses.get(request.callKey());
        if (content == null) throw new NonRetryableException("No scripted response for " + request.callKey());
        return new LlmResponse(content, new TokenUsage(10, 5), "map", Duration.ofMillis(1));
    }

    @Override public String modelName() { return "map"; }
    @Override public boolean isMock() { return true; }
}