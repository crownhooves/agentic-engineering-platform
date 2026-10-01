# Agentic Engineering Platform

An AI-powered agentic software engineering platform that turns a software requirement into a reviewable engineering outcome. It analyzes the requirement, plans dependency-aware tasks, executes them through specialized AI agents, validates the generated code, assesses risks, and produces an auditable engineering summary and a downloadable output package.

It demonstrates an agentic SDLC rather than a chatbot or single-prompt code generator: **controlled autonomy**, where agents work independently across multiple steps inside explicit budgets and limits while humans retain review and approval.

> **Demo requirement:** *"Build a scalable URL shortener service with REST APIs, persistence, and analytics."*

| | |
|---|---|
| Backend (this repo) | Java 17, Spring Boot, Spring AI, H2/JPA, Actuator/Micrometer |
| UI | [agentic-engineering-platform-ui](https://github.com/crownhooves/agentic-engineering-platform-ui) (Next.js, SSE) |
| LLM | Local Ollama, `qwen2.5-coder:7b` |
| Docs | [Architecture](#architecture) · [Testing](#testing-and-validation) · [Examples](#example-scenarios) · [Limitations](#limitations-and-trade-offs) |



---

## Table of contents

1. [Quick start](#quick-start)
2. [What it does](#what-it-does)
3. [Demonstration: URL shortener](#demonstration-url-shortener)
4. [Architecture](#architecture)
5. [Observability](#observability)
6. [Key decisions and trade-offs](#key-decisions-and-trade-offs)
7. [Example scenarios](#example-scenarios)
8. [Testing and validation](#testing-and-validation)
9. [Reliability lessons](#reliability-lessons)
10. [Limitations and trade-offs](#limitations-and-trade-offs)
11. [Production roadmap](#production-roadmap)
12. [Repository structure](#repository-structure)

---

## Quick start

**Prerequisites:** Java 17, [Ollama](https://ollama.com), Node.js 22+ (for the UI).

```bash
# 1. Model
ollama pull qwen2.5-coder:7b
ollama serve                      # skip if already running; listens on :11434

# 2. Backend (this repo) -> http://localhost:8088
./mvnw spring-boot:run -Dspring-boot.run.profiles=ollama

# 3. UI (separate repo) -> http://localhost:3002
git clone https://github.com/crownhooves/agentic-engineering-platform-ui.git
cd agentic-engineering-platform-ui
echo "NEXT_PUBLIC_API_BASE_URL=http://localhost:8088" > .env.local
npm install && npm run dev
```

Open <http://localhost:3002>, submit the URL shortener requirement, and watch the run execute.

- The UI runs on port **3002** because the backend's CORS configuration allows that origin.
- Local inference is slow; a full run can take several minutes depending on hardware. `[VERIFY: add typical duration]`
- Optional monitoring stack (Prometheus and Grafana) is described under [Observability](#observability).

---

## What it does

Given a software engineering requirement, the platform:

1. Analyzes the requirement and surfaces ambiguities and assumptions.
2. Creates an execution plan and decomposes it into dependency-aware tasks.
3. Assigns tasks to specialized agents and runs independent tasks in parallel.
4. Generates architecture, implementation, analytics, and unit and integration tests.
5. Compiles and tests the generated code, repairing and revalidating on failure.
6. Assesses risks and failure scenarios.
7. Retries failed agent tasks with backoff, and escalates when retries are exhausted.
8. Records an execution and governance audit trail.
9. Produces a structured engineering summary.
10. Packages the engineering output as a **downloadable artifact**.

```
Requirement → Agentic execution → Implementation → Tests → Validation
            → Risk assessment → Engineering summary → Evidence → Download
```

---

## Demonstration: URL shortener

The requirement is decomposed into eight tasks:

| # | Task | Purpose |
|---|---|---|
| 1 | `design-architecture` | Components, API contract, data model |
| 2 | `implement-core` | Create/redirect service and persistence |
| 3 | `implement-analytics` | Click analytics (parallel branch) |
| 4 | `write-unit-tests` | Unit tests (parallel branch) |
| 5 | `write-integration-tests` | Integration tests |
| 6 | `validate-build` | Compile and run tests, capture evidence |
| 7 | `assess-risks` | Risks, trade-offs, failure scenarios |
| 8 | `summarize` | Structured engineering summary |

Reference run `4246c9b1-7551-4d99-a2f8-8bdf53b84264`: **8/8 tasks completed, 0 failed, 0 escalated, 0 active.** The sample output is in `reference/sample`. `[VERIFY: describe it, and state whether the generated code compiled and its tests passed]`

---

## Architecture

### System context

```
Browser ──HTTP/SSE──> Next.js dashboard (:3002)
                          │ REST + SSE
                          ▼
                 Spring Boot backend (:8088)
        ┌─────────────────────────────────────────────┐
        │ Agent Orchestrator (DAG scheduler)           │
        │   ├─ Specialized agents (Spring AI)          │
        │   ├─ Validation / Repair / Evidence          │
        │   └─ H2 + JPA (./data/agentdb)               │
        │ Actuator + Micrometer (/actuator/prometheus) │
        └───────────────┬─────────────────────────────┘
                        │ Spring AI
                        ▼
              Ollama (:11434, native on host)
                 qwen2.5-coder:7b

Prometheus (:9090) ──scrapes──> backend ──> Grafana (:3000)
```

### Agents

| Agent / stage | Responsibility |
|---|---|
| Requirement Analyst | Interprets intent, normalizes the requirement, surfaces ambiguities and assumptions |
| Planner | Produces dependency-aware tasks and execution order |
| Architecture | Designs components, API contract and data model |
| Implementation (core) | Generates the core service code |
| Analytics | Generates the analytics feature |
| Test (unit, integration) | Generates unit and integration tests |
| Build Validation | Materializes output, compiles and runs tests, captures evidence |
| Risk Assessment | Identifies risks, trade-offs and failure scenarios |
| Summary | Produces the structured engineering summary |

### Execution model

```
Requirement → Analyst → Planner → design-architecture → implement-core
                                                     ├→ implement-analytics ┐
                                                     └→ write-unit-tests ───┤
                                   write-integration-tests ←────────────────┘
                                                     ↓
                                   validate-build → assess-risks → summarize
```
`[VERIFY: adjust to the exact dependency edges your planner emits]`

- **Scheduling:** a task runs once all its dependencies complete. Independent tasks run in parallel up to `max-parallel-tasks` (4). This is a DAG, not a linear chain, so one branch failing does not silently corrupt another.
- **Retries:** exponential backoff with `max-attempts: 3`, `initial-backoff: 500ms`, `max-backoff: 10s`.
- **Timeouts:** per-step timeout (180s) and a separate, longer LLM-call timeout for local inference.
- **Output repair:** malformed or schema-invalid LLM output goes through a bounded structured-output repair step.
- **Escalation:** tasks that exhaust retries are escalated, never assumed successful.

### Closed-loop validation

```
Materialize → Compile/Test → Capture evidence → Pass? ─ yes → continue
                                                  └ no → Repair → Revalidate
```

Generated code is never assumed correct. Success, failed validation, retry, repair and escalation are distinct, recorded outcomes.

### Controlled autonomy and guardrails

| Guardrail | Purpose |
|---|---|
| Per-run token budget | Caps cost and runaway generation |
| Per-run LLM-call budget | Caps loops and repeated repair |
| Max output repairs | Prevents infinite repair cycles |
| Workspace, max output size, max files | Contains what generated code can write |
| Step and LLM timeouts | Prevents hung runs |
| Human review/approval states | Humans validate outcomes. `[VERIFY: is the run blocked until approval, or is it a recorded state? State which.]` |
| Evidence and audit events | Every decision is traceable |

### Evidence and auditability

Each run records the submitted requirement, tasks created, which agent executed each, task state, attempts, validation results, the risk assessment, the summary and generated artifacts. A completed run can be packaged and downloaded from the UI for offline review.

---

## Observability

The backend exposes `/actuator/health` and `/actuator/prometheus`, including AI-workload metrics:

| Metric | Dimensions |
|---|---|
| `agent_llm_calls_total` | agent, model, outcome |
| `agent_llm_latency_seconds` | agent, model |
| `agent_llm_tokens_total` | agent, model, type (prompt/completion) |

Prometheus configuration is in `monitoring/prometheus`. Grafana dashboards cover LLM calls, errors, call rate, latency, prompt/completion tokens and calls by agent, plus HTTP rate/latency/errors, JVM heap/CPU/threads, Hikari pool and system load. This answers "which agent is slow, failing or expensive?" and not only "is the JVM healthy?"

When the backend runs on the host and Prometheus in a container, Prometheus scrapes `host.docker.internal:8088` and Grafana reads `host.docker.internal:9090`. `[VERIFY: what docker-compose.yml starts, and the command to run it]`

`[ADD: Grafana screenshot]`

---

## Key decisions and trade-offs

| Decision | Rationale | Trade-off |
|---|---|---|
| Java 17 / Spring Boot / Spring AI | Enterprise-standard stack with strong ops tooling | More ceremony than Python agent frameworks |
| Local Ollama, `qwen2.5-coder:7b` | No API cost or data egress; reproducible locally | Weaker output; slower; needs capable hardware |
| Ollama native, app containerized | Docker on macOS cannot use the Metal GPU; a containerized Ollama falls back to CPU | Single-command full-stack compose is not the demo path |
| H2, file-backed | Zero setup, persists between runs | Not for concurrency or HA |
| SSE instead of polling | Real-time updates with simple infrastructure | One-way; scaling needs a broker |
| Separate UI and backend repos | Independent deploy; CORS-bounded contract | Two repos to run |
| In-process DAG orchestration | Parallelism and explicit failure semantics | Not durable across restarts `[VERIFY]` |

### Why Ollama runs outside Docker

This was deliberate. Docker on macOS cannot access the Metal GPU, so a containerized Ollama runs on CPU and is impractically slow. Ollama runs natively; the backend and UI can be containerized and reach it at `host.docker.internal:11434`. Dockerfiles are provided for both app tiers; the documented demo path is the native run above. `[VERIFY: confirm the backend container reaches Ollama this way and document the env var that sets the URL]`

Container build for the UI needs the API URL at build time, because Next.js bakes `NEXT_PUBLIC_*` values in:
`docker build --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8088 -t agentic-engineering-platform-ui .` `[VERIFY: confirm the UI Dockerfile declares this ARG]`

---

## Example scenarios

### 1. Greenfield (implemented)

**Input:** *Build a scalable URL shortener service with REST APIs, persistence, and analytics.*

**Decomposition and orchestration:** the eight-task DAG above, with analytics and unit tests running in parallel after implementation, then integration tests, build validation, risk assessment and summary.

**Output:** architecture and API contract, service code, analytics, unit and integration tests, validation evidence, risk assessment, summary and a downloadable package. See `reference/sample`.

**Validation:** compile and test in a bounded workspace, recorded as evidence.

### 2. Brownfield (design-level) `[VERIFY: change if implemented]`

**Input:** *Add click-expiry (TTL) to existing short links and expose it in the API.*

**Intended flow:**
1. Index the target repository (modules, controllers, entities, schema).
2. Impact analysis: affected API (`POST /links`), entity and migration, redirect path, analytics.
3. Scoped tasks: schema migration, domain change, API contract update, expiry and backward-compatibility tests.
4. Minimal diff, validated by running existing plus new tests.
5. Human approval before anything is applied.

**Status:** codebase indexing is not implemented; the platform currently generates new artifacts rather than patching an existing repo.

### 3. Ambiguous `[VERIFY: replace with real output]`

**Input:** *Make links better.*

**Expected behavior:** the Requirement Analyst reports that it cannot form a testable problem statement, lists ambiguities (does "better" mean speed, UX, security or analytics?), proposes explicit assumptions, and asks for human clarification before planning.

`[BEST: run this on the live system, screenshot the analyst output, and replace this section. If the run proceeds without pausing, say so under Limitations.]`

---

## Testing and validation

Validation happens at four layers, because there are four ways the system can be wrong.

1. **Platform correctness.** Tests for the orchestrator: DAG ordering, retry/backoff, timeouts, budget enforcement, state transitions, repair-loop bounds. Run with `./mvnw test`. `[VERIFY: list the test classes that exist]`
2. **LLM output validity.** Every agent response is parsed and schema-validated; malformed output goes through a bounded repair step; if still invalid the task retries per policy, then escalates.
3. **Generated artifact validation.** `validate-build` materializes code into a bounded workspace, compiles it, runs the generated unit and integration tests and records the result as evidence. Failures trigger repair and revalidation.
4. **Run-level acceptance.** A run is acceptable when all tasks reach a terminal state with evidence, the build/test outcome is recorded (pass, or fail with reason), the risk assessment and summary reference real artifacts, budgets were respected, and a human can review the package.

**To evaluate it yourself:** follow the Quick start, submit the URL shortener requirement, watch the DAG execute, inspect the validation evidence, risk assessment and summary, download the package, and check `/actuator/prometheus` for `agent_llm_*` metrics.

**Planned improvements:** an eval harness (fixed requirements run on every change, tracking success, repair rate, tokens and latency); mutation or coverage checks on generated tests; static analysis and dependency scanning of generated code; fault injection for LLM timeouts and malformed output.

---

## Reliability lessons

An early run failed in ways typical of LLM agents:

| Failure | Mitigation |
|---|---|
| Malformed or truncated JSON | Structured-output parsing with a bounded repair step |
| Schema mismatch | Schema validation before a task result is accepted |
| Planner drift away from the requirement | Requirement-fidelity checks on the plan `[VERIFY]` |
| Transient failures | Retry with exponential backoff, then escalation |

The design principle is that model output is untrusted input.

---

## Limitations and trade-offs

- **Brownfield:** no repository indexing or impact analysis yet; see the intended design above.
- **Model quality:** a 7B model gives variable, sometimes mediocre code, and results differ between runs. The platform's contribution is detecting and reporting problems through validation, not guaranteeing quality.
- **Test depth:** generated tests can be shallow; passing tests do not prove correctness. There is no golden-output regression suite.
- **Persistence:** H2 file-backed for zero setup.
- **Execution:** in-process orchestration, not durable across restarts. `[VERIFY]`
- **Resilience:** no circuit breaker or fallback model yet.
- **Security:** generated code runs in a local, size-limited workspace; no sandboxing or authentication.
- **Ambiguity handling and approval gate:** `[VERIFY: state exactly what is implemented]`

---

## Production roadmap

| Area | Prototype | Production |
|---|---|---|
| Database | H2 | PostgreSQL |
| Execution | In-process | Queue-based, durable, restart-safe |
| Model | Local 7B | Stronger model with a fallback and a circuit breaker |
| Code execution | Local workspace | Sandboxed containers, secrets handling, authn/authz |
| Quality | Manual review | Eval harness, mutation testing, security scanning |
| Brownfield | Not implemented | Repo indexing, impact analysis, scoped diffs |

**Proposed SLOs:** run success rate, repair rate, p95 run duration, tokens (cost) per run and escalation rate, with burn-rate alerts on each.

---

## Repository structure

```
.
├── src/                    Spring Boot application (orchestrator, agents, validation)
├── reference/sample/       Sample generated engineering output
├── monitoring/prometheus/  Prometheus configuration
├── Dockerfile              Backend container (multi-stage Maven build)
├── docker-compose.yml      [VERIFY: what it starts]
├── pom.xml / mvnw          Build
└── README.md
```

**Related repository:** UI at <https://github.com/crownhooves/agentic-engineering-platform-ui>
