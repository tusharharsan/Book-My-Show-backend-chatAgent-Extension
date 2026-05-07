# BookMyShow — Spring Boot + Spring AI Reference Project

A teaching-oriented clone of BookMyShow's core ticketing flow, built as a **Spring Boot 3** REST/WebSocket service with an **AI booking agent ("BookBot")** layered on top using **Spring AI**.

The agent can hold a real conversation — *"What's playing tonight in Mumbai?"* → *"book me 2 platinum seats"* → *"pay with UPI"* — and drives the booking by calling the same backend services that the REST endpoints expose.

---

## Table of Contents

1. [What This Project Demonstrates](#what-this-project-demonstrates)
2. [How It Works (High-Level)](#how-it-works-high-level)
3. [Project Structure](#project-structure)
4. [Prerequisites](#prerequisites)
5. [Setup & Run](#setup--run)
6. [Running with Ollama (No API Key Needed)](#running-with-ollama-no-api-key-needed)
7. [API Testing with Postman](#api-testing-with-postman)
8. [Key Endpoints](#key-endpoints)
9. [Configuration Reference](#configuration-reference)
10. [Docker](#docker)
11. [Troubleshooting](#troubleshooting)

---

## What This Project Demonstrates

This repo is structured to teach four things at once:

| Concept | Where it shows up |
| --- | --- |
| Classic Spring Boot CRUD app (JPA, REST, layered services) | `controllers/`, `services/`, `repositories/`, `models/` |
| Spring AI `ChatClient` abstraction over multiple LLM providers | `ai/agent/BookMyShowAgentService.java` |
| Tool-calling pattern: **Domain Contracts → Tools Facade → Service** | `ai/contracts/`, `ai/tools/`, existing `services/` |
| Resilience4j-based **automatic provider failover** | `@Retry` annotation on `processMessage(...)` |

Provider failover chain: **Gemini → Anthropic → OpenAI → Ollama**. If the primary fails, Resilience4j routes the call to the next provider transparently — the user sees one reply.

---

## How It Works (High-Level)

```
                     ┌────────────────────────────────────────────┐
HTTP / WebSocket ──▶ │  AgentController  /  ChatWebSocketHandler  │
                     └──────────────────────┬─────────────────────┘
                                            │
                                            ▼
                     ┌────────────────────────────────────────────┐
                     │          BookMyShowAgentService            │
                     │  • One ChatClient per provider             │
                     │  • Shared MessageWindowChatMemory          │
                     │  • @Retry → fallbackChain on failure       │
                     └──────────────────────┬─────────────────────┘
                                            │
        ┌───────────────────┬───────────────┼───────────────────┐
        ▼                   ▼               ▼                   ▼
   Gemini (Vertex)     Anthropic         OpenAI              Ollama (local)

         All four ChatClients share the same toolbox  ↓

                     ┌────────────────────────────────────────────┐
                     │   ai/tools/  (DiscoveryTools, TicketingTools,
                     │             PaymentTools, UserTools)       │
                     │   @Tool methods exposed to the LLM         │
                     └──────────────────────┬─────────────────────┘
                                            ▼
                     ┌────────────────────────────────────────────┐
                     │       services/  (MovieService, ...)       │
                     │       Plain Spring services + JPA          │
                     └──────────────────────┬─────────────────────┘
                                            ▼
                                       MySQL (bmsoct24)
```

### Important pieces

- **`BookMyShowAgentService`** — the orchestrator. Builds **four** `ChatClient` beans (one per provider), wires them all to the same `ChatMemory` and the same toolbox, and routes calls. The `@Retry(name = "aiModelFailover", fallbackMethod = "fallbackChain")` annotation makes the failover automatic.
- **`ai/tools/*Tools.java`** — thin facades annotated with Spring AI's `@Tool`. Each method:
  1. Takes a typed request record from `ai/contracts/`,
  2. Calls a normal Spring service method,
  3. Returns a `Map<String, Object>` result the LLM can read.
  Business logic stays in `services/` — the tools layer only adapts.
- **`chat/ToolGuardService`** — runtime state machine guard: `IDLE → BOOKING_PENDING → BOOKING_CONFIRMED`. Even if the LLM hallucinates a `process_payment` call before `create_booking`, this rejects it.
- **`chat/ChatRequestContext`** — `ThreadLocal` that holds the authenticated `userId` for the request. Tools read it from here, **never** from LLM-supplied input. This is the prompt-injection mitigation: a malicious user typing *"book this for user 7"* can't pivot the booking off their own account.
- **`prompts/bookbot-system.st`** — the system prompt template. Defines persona, allowed seat types, payment modes, confirmation rules.

---

## Project Structure

```
BookMyShow/
├── pom.xml                              ← Spring Boot 3.3.4 + Spring AI 1.0.0 BOM
├── Dockerfile                           ← Multi-stage build (JDK 17 → JRE 17)
├── BookMyShow.postman_collection.json   ← Importable API test suite
├── src/main/
│   ├── java/com/example/bookmyshowoct24/
│   │   ├── BookMyShowOct24Application.java    ← Spring Boot entry point
│   │   │
│   │   ├── ai/                          ← All AI-related code lives here
│   │   │   ├── agent/
│   │   │   │   └── BookMyShowAgentService.java   ← Orchestrator + failover
│   │   │   ├── contracts/                ← Typed request records for tools
│   │   │   │   ├── DiscoveryContracts.java
│   │   │   │   ├── TicketingContracts.java
│   │   │   │   └── PaymentContracts.java
│   │   │   └── tools/                    ← @Tool facades exposed to the LLM
│   │   │       ├── DiscoveryTools.java   (search_movies, get_shows, get_show_seats)
│   │   │       ├── TicketingTools.java   (create_booking, cancel_booking)
│   │   │       ├── PaymentTools.java     (apply_coupon, process_payment)
│   │   │       └── UserTools.java        (get_my_bookings, etc.)
│   │   │
│   │   ├── chat/                         ← Conversation infrastructure
│   │   │   ├── ChatWebSocketHandler.java
│   │   │   ├── WebSocketConfig.java
│   │   │   ├── SessionManager.java
│   │   │   ├── ChatRequestContext.java   ← ThreadLocal for userId / sessionId
│   │   │   ├── ConversationState.java    ← IDLE / BOOKING_PENDING / BOOKING_CONFIRMED
│   │   │   └── ToolGuardService.java     ← State-machine guard
│   │   │
│   │   ├── controllers/                  ← REST entry points
│   │   │   ├── AgentController.java      (POST /api/agent/chat)
│   │   │   ├── BookingController.java
│   │   │   ├── CouponController.java
│   │   │   ├── MovieController.java
│   │   │   ├── PaymentController.java
│   │   │   ├── ShowController.java
│   │   │   └── UserController.java
│   │   │
│   │   ├── services/                     ← Business logic (LLM-agnostic)
│   │   ├── repositories/                 ← Spring Data JPA repositories
│   │   ├── models/                       ← JPA entities
│   │   ├── dtos/                         ← Request / response DTOs
│   │   ├── exceptions/ + exceptionhandler/
│   │   ├── configs/                      ← Security, password encoder
│   │   └── seed/                         ← Optional seed data
│   │
│   └── resources/
│       ├── application.properties        ← DB + four-provider AI config
│       └── prompts/
│           └── bookbot-system.st         ← System prompt for BookBot
│
└── src/test/                             ← Spring Boot tests
```

---

## Prerequisites

- **Java 17+** (Eclipse Temurin recommended)
- **Maven** — bundled `./mvnw` works, no global install required
- **MySQL 8.x** running on `localhost:3306` with a database named `bmsoct24`
  - Default user: `root`, password: `1234` (override via `MYSQL_HOST` env var or edit `application.properties`)
- **One of**:
  - **Ollama** running locally (recommended for testing without paying for API calls — see next section), OR
  - An API key for Anthropic / OpenAI, OR
  - A Google Cloud project with Vertex AI enabled

---

## Setup & Run

```bash
# 1. Clone and enter
cd BookMyShow

# 2. Start MySQL and create the schema
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS bmsoct24;"

# 3. (Optional) export an API key — otherwise see Ollama section below
export ANTHROPIC_API_KEY=sk-ant-...

# 4. Build and run
./mvnw spring-boot:run
```

The app boots on **port 8080**. JPA `ddl-auto=update` creates tables on first start.

> **Note on dummy keys**: `application.properties` provides `:dummy-...` defaults for every provider so the Spring context comes up even with zero credentials. Calls will fail until you set a real key — but the app will boot, which is what you want during development.

---

## Running with Ollama (No API Key Needed)

This is the **recommended path for local testing and classroom demos** — no credit card, no API quotas, fully offline.

### 1. Install Ollama

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows
# Download installer from https://ollama.com/download
```

### 2. Pull a tool-capable model

> **Pick a 7B+ model.** Smaller models (1.5B / 3B) often emit free-text instead of calling a tool. The harness will look broken when it isn't.

```bash
ollama pull qwen3.5:9b
```

(Other good choices: `llama3.1:8b`, `mistral:7b-instruct`. If you change models, update `spring.ai.ollama.chat.options.model` in `application.properties`.)

### 3. Start the Ollama daemon

```bash
ollama serve
```

This binds to `http://localhost:11434` — already wired into `application.properties` via `OLLAMA_BASE_URL`.

### 4. Run the Spring app **without any API keys**

```bash
./mvnw spring-boot:run
```

### What happens at runtime

Because no real keys are exported:
- Gemini call → fails (dummy project)
- Anthropic call → fails (dummy key)
- OpenAI call → fails (dummy key)
- **Ollama call → succeeds** ✅

So every chat turn cascades through three failures (~1–3 s extra) and Ollama answers. That's deliberate — it's how you **see the failover chain in action** end-to-end. For faster local iteration, set one real key and the chain short-circuits at the first working provider.

### Faster path (skip the cascade)

If you want only Ollama to be tried, edit `application.properties` to point Gemini/Anthropic/OpenAI clients at the same broken endpoint and let them fail instantly — or simply expect a small per-turn delay. In a teaching context, the delay is the lesson.

---

## API Testing with Postman

This repo ships with a complete Postman collection: **`BookMyShow.postman_collection.json`** (root of the repo).

### Importing

1. Open Postman → **Import** → **File** → pick `BookMyShow.postman_collection.json`.
2. Make sure the collection variable `baseUrl` is set to `http://localhost:8080`.
3. Set `userId` to a valid user id (or run *REST → Users → Sign Up* first to auto-capture one).

### What's inside

The collection is split into folders:

| Folder | Purpose |
| --- | --- |
| **🤖 Agent — Real-world booking conversation** | Multi-turn chat against `POST /api/agent/chat`. Run requests **in order** with the same `sessionId` — exercises chat memory, tool dispatch, state machine, and failover. |
| **🤖 Agent — Edge cases & guardrails** | Prompt-injection attempts, state-machine violations (e.g. paying before booking), wrong-user attacks. All should be **rejected by the harness, not the model**. |
| **🤖 Agent — Quick single-shot examples** | One-turn prompts for sanity-checking a single tool path. |
| **REST — End-to-end (no LLM)** | The full booking flow without any AI — IDs auto-chain through Postman test scripts. Use this when LLMs are offline. |
| **REST — individual endpoint catalogs** | Users / Movies / Shows / Bookings / Payments / Coupons. Fire-and-forget for ad-hoc testing. |

### Recommended demo run

1. Run *REST → Users → Sign Up* to populate `userId`.
2. Seed a movie + show + AVAILABLE seats (use the REST catalog folders, or write a SQL seed).
3. Open *🤖 Agent — Real-world booking conversation* and run each request top-to-bottom. Watch the server logs to see which provider is answering and which tool is being dispatched.

---

## Key Endpoints

### AI Agent

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/agent/chat` | Send a chat turn. Body: `{"sessionId": "...", "userId": 1, "content": "..."}` |
| `WS`   | `/ws/agent`       | WebSocket alternative — same protocol but streamed |

> Reuse the same `sessionId` across turns to preserve chat memory (40-message window).

### REST (the agent calls these under the hood, but you can hit them directly)

| Resource | Endpoints |
| --- | --- |
| Users    | `POST /users/signup`, `POST /users/login` |
| Movies   | `GET /movies`, `GET /movies/{id}` |
| Shows    | `GET /shows`, `GET /shows/{id}/seats` |
| Bookings | `POST /bookings`, `DELETE /bookings/{id}` |
| Payments | `POST /payments` |
| Coupons  | `POST /coupons/apply` |

(See `controllers/` for the exact signatures, or use the Postman collection.)

---

## Configuration Reference

All configuration lives in **`src/main/resources/application.properties`**. Every value can be overridden by an environment variable.

| Property | Env var | Default | Notes |
| --- | --- | --- | --- |
| `spring.datasource.url` | `MYSQL_HOST` | `localhost` | MySQL host |
| `spring.ai.vertex.ai.gemini.project-id` | `VERTEX_AI_PROJECT_ID` | `dummy-project` | GCP project. Auth via `gcloud auth application-default login` |
| `spring.ai.anthropic.api-key` | `ANTHROPIC_API_KEY` | `dummy-anthropic-key` | |
| `spring.ai.openai.api-key` | `OPENAI_API_KEY` | `dummy-openai-key` | |
| `spring.ai.ollama.base-url` | `OLLAMA_BASE_URL` | `http://localhost:11434` | |
| `spring.ai.ollama.chat.options.model` | — | `qwen3.5:9b` | Must match what you `ollama pull`'d |
| `resilience4j.retry.instances.aiModelFailover.max-attempts` | — | `1` | `1` = no retry on primary, immediate fallback |

---

## Docker

A multi-stage `Dockerfile` is included. It builds the fat jar with Maven and runs it on a minimal JRE 17 image.

```bash
docker build -t bookmyshow .
docker run -p 8080:8080 \
  -e MYSQL_HOST=host.docker.internal \
  -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY \
  bookmyshow
```

The image binds to `$PORT` (defaults to 8080), which makes it ready for platforms like Render, Fly.io, or Cloud Run.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
| --- | --- | --- |
| `No qualifying bean of type 'ChatModel'` | A provider starter didn't auto-configure | Check that the corresponding env var is set (real or dummy) |
| `IllegalStateException: No @Tool annotated methods found in <ToolsClass>` | A `@Tool` method returns raw `Object` (Spring AI thinks it's a `Function`) | Return `Map<String, Object>` instead |
| Ollama responds with free text instead of calling a tool | Model is too small to do tool-calling reliably | Use a 7B+ model (e.g. `qwen3.5:9b`, `llama3.1:8b`) |
| `Tool 'process_payment' cannot be called in state IDLE` | LLM skipped `create_booking` | This is the `ToolGuardService` working as designed — the model will retry correctly after seeing the error |
| Chat replies don't remember earlier turns | `sessionId` changes per request | Reuse the same `sessionId` across all turns of one conversation |
| App fails to start with DevTools-related classloader error | DevTools wraps `@Tool` annotation in a separate classloader | DevTools is intentionally **not** included — don't add `spring-boot-devtools` |

---

## License

For educational use as part of the Scaler Spring Boot / Spring AI curriculum.