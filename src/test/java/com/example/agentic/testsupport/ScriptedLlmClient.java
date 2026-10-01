package com.example.agentic.testsupport;

import com.example.agentic.llm.*;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Returns pre-scripted responses in order and records every request it receives. */
public class ScriptedLlmClient implements LlmClient {

    public final List<LlmRequest> requests = new ArrayList<>();
    private final Deque<String> script = new ArrayDeque<>();
    private final boolean mock;

    public ScriptedLlmClient(boolean mock, String... responses) {
        this.mock = mock;
        script.addAll(List.of(responses));
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        String next = script.poll();
        if (next == null) throw new IllegalStateException("Script exhausted at call " + requests.size());
        return new LlmResponse(next, new TokenUsage(100, 50), "scripted", Duration.ofMillis(1));
    }

    @Override public String modelName() { return "scripted"; }
    @Override public boolean isMock() { return mock; }
}