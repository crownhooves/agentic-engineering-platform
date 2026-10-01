package com.example.agentic.agents;

/** A DAG-executable agent. Reads inputs from and writes outputs to the RunContext blackboard. */
public interface Agent {
    AgentType type();
    void execute(AgentContext ctx) throws Exception;
}