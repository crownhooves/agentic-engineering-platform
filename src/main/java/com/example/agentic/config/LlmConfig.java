package com.example.agentic.config;

import com.example.agentic.llm.StructuredOutputParser;
import com.example.agentic.observability.AgentMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Bean
    StructuredOutputParser structuredOutputParser() {
        return StructuredOutputParser.withDefaults();
    }

    /** Falls back to an in-memory registry if Actuator's metrics auto-config is absent. */
    @Bean
    AgentMetrics agentMetrics(ObjectProvider<MeterRegistry> registry) {
        return new AgentMetrics(registry.getIfAvailable(SimpleMeterRegistry::new));
    }
}