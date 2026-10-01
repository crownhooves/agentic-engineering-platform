package com.example.agentic.llm;

import com.example.agentic.common.InvalidOutputException;
import com.example.agentic.common.NonRetryableException;
import com.example.agentic.config.LlmProperties;
import com.example.agentic.guardrails.AuditLogger;
import com.example.agentic.guardrails.BudgetExceededException;
import com.example.agentic.guardrails.BudgetGuard;
import com.example.agentic.guardrails.SecretRedactor;
import com.example.agentic.observability.AgentMetrics;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;
import java.util.function.Function;

/**
 * The only path from an agent to the model. Enforces budget, redacts secrets, writes the audit trail,
 * records metrics and re-prompts with the validation error when the output is malformed.
 */
@Component
public class LlmGateway {

    private final LlmClient client;
    private final StructuredOutputParser parser;
    private final BudgetGuard budget;
    private final SecretRedactor redactor;
    private final AuditLogger audit;
    private final AgentMetrics metrics;
    private final int maxOutputRepairs;

    public LlmGateway(LlmClient client, StructuredOutputParser parser, BudgetGuard budget,
                      SecretRedactor redactor, AuditLogger audit, AgentMetrics metrics,
                      LlmProperties props) {
        this.client = client;
        this.parser = parser;
        this.budget = budget;
        this.redactor = redactor;
        this.audit = audit;
        this.metrics = metrics;
        this.maxOutputRepairs = props.maxOutputRepairs();
    }



    /** Free-text call (e.g. code files in a delimited format). Same guardrails, no parsing. */
    public String callText(LlmRequest request) {
        return invoke(request).content();
    }

    private LlmResponse invoke(LlmRequest raw) {
        try {
            budget.checkBeforeCall(raw.runId());
        } catch (BudgetExceededException e) {
            audit.log(raw.runId(), actor(raw), "GUARDRAIL_BLOCKED", e.getMessage());
            throw e;
        }

        LlmRequest req = new LlmRequest(raw.runId(), raw.scenario(), raw.agent(), raw.callKey(),
                redactor.redact(raw.systemPrompt()), redactor.redact(raw.userPrompt()), raw.jsonMode());
        String actor = actor(req);
        audit.log(req.runId(), actor, "LLM_REQUEST",
                "callKey=" + req.callKey() + " systemPromptChars=" + length(req.systemPrompt())
                        + "\n" + req.userPrompt());

        long start = System.nanoTime();
        try {
            LlmResponse response = client.complete(req);
            budget.recordUsage(req.runId(), response.usage());
            metrics.recordLlmCall(req.agent(), response.model(), response.usage(), response.latency(), "success");
            audit.log(req.runId(), actor, "LLM_RESPONSE",
                    "callKey=" + req.callKey() + " model=" + response.model()
                            + " promptTokens=" + response.usage().promptTokens()
                            + " completionTokens=" + response.usage().completionTokens()
                            + "\n" + response.content());
            return response;
        } catch (RuntimeException e) {
            metrics.recordLlmCall(req.agent(), client.modelName(), TokenUsage.ZERO,
                    Duration.ofNanos(System.nanoTime() - start), "error");
            audit.log(req.runId(), actor, "LLM_ERROR", "callKey=" + req.callKey() + " " + e);
            throw e;
        }
    }


    /** Call the model and parse the answer into {@code type}, re-prompting on malformed output. */
    public <T> T callJson(LlmRequest request, Class<T> type) {
        return callParsed(request, raw -> parser.parse(raw, type), type.getSimpleName(),
                "Return ONLY the corrected JSON document. No markdown fences, no commentary.");
    }

    /** Generic form: any parser that throws InvalidOutputException gets the same re-prompt loop. */
    public <T> T callParsed(LlmRequest request, Function<String, T> parse, String typeName,
                            String correctionHint) {
        InvalidOutputException lastError = null;
        String lastRaw = null;
        for (int attempt = 0; attempt <= maxOutputRepairs; attempt++) {
            LlmRequest current = attempt == 0
                    ? request : repairRequest(request, lastRaw, lastError, attempt, correctionHint);
            LlmResponse response = invoke(current);
            try {
                return parse.apply(response.content());
            } catch (InvalidOutputException e) {
                lastError = e;
                lastRaw = response.content();
                metrics.recordInvalidOutput(request.agent());
                audit.log(request.runId(), actor(request), "OUTPUT_INVALID",
                        "callKey=" + current.callKey() + " error=" + e.getMessage());
            }
        }
        String message = "No valid " + typeName + " after " + (maxOutputRepairs + 1)
                + " attempts: " + lastError.getMessage();
        if (client.isMock()) throw new NonRetryableException(message, lastError);
        throw new InvalidOutputException(message, lastError);
    }

    private static LlmRequest repairRequest(LlmRequest original, String previous,
                                            InvalidOutputException error, int attempt, String hint) {
        String prompt = original.userPrompt()
                + "\n\n---\nYour previous response was rejected: " + error.getMessage()
                + "\n\nPrevious response (truncated):\n" + truncate(previous, 3000)
                + "\n\n" + hint;
        return new LlmRequest(original.runId(), original.scenario(), original.agent(),
                original.callKey() + "-reprompt-" + attempt,
                original.systemPrompt(), prompt, original.jsonMode());
    }

    private static String actor(LlmRequest r) { return r.agent().name().toLowerCase(Locale.ROOT); }
    private static int length(String s) { return s == null ? 0 : s.length(); }
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}