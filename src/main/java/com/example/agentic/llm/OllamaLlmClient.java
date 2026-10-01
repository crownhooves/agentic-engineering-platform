package com.example.agentic.llm;

import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Live mode. Uses ChatModel with explicit message objects rather than ChatClient's string DSL,
 * so braces in JSON/code inside prompts are never interpreted as template placeholders.
 * jsonMode is advisory here: the prompts demand JSON and StructuredOutputParser + the gateway's
 * re-prompt loop handle non-compliance.
 */
@Component
@Profile("ollama")
public class OllamaLlmClient implements LlmClient {

    private final ChatModel chatModel;
    private final String model;

    public OllamaLlmClient(ChatModel chatModel,
                           @Value("${spring.ai.ollama.chat.options.model:ollama}") String model) {
        this.chatModel = chatModel;
        this.model = model;
    }

    @Override
    public LlmResponse complete(LlmRequest req) {
        List<Message> messages = List.of(new SystemMessage(req.systemPrompt()), new UserMessage(req.userPrompt()));
        long start = System.nanoTime();
        ChatResponse response = chatModel.call(new Prompt(messages));
        Duration latency = Duration.ofNanos(System.nanoTime() - start);

        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("Ollama returned an empty response");
        }
        String content = response.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Ollama returned blank content");
        }
        return new LlmResponse(content, usageOf(response), model, latency);
    }

    private static TokenUsage usageOf(ChatResponse response) {
        if (response.getMetadata() == null) return TokenUsage.ZERO;
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) return TokenUsage.ZERO;
        return new TokenUsage(toInt(usage.getPromptTokens()), toInt(usage.getCompletionTokens()));
    }

    private static int toInt(Number n) { return n == null ? 0 : n.intValue(); }

    @Override public String modelName() { return model; }
    @Override public boolean isMock() { return false; }
}