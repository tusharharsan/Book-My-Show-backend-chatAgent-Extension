# Product Requirements Document (PRD)
# BookMyShow AI Extensions — Agentic AI Booking Assistant (Chat + Voice Call)

| Field              | Detail                                                            |
|--------------------|-------------------------------------------------------------------|
| **Document Version** | 1.0                                                             |
| **Date**             | April 15, 2026                                                  |
| **Author**           | Tushar Harsan                                                   |
| **Status**           | Draft                                                           |
| **Target Audience**  | Backend students extending the base BookMyShow LLD project      |
| **Stack**            | Java 17+ · Spring Boot 3.x · Maven · REST APIs · WebSocket      |
| **AI Providers**     | Anthropic Claude API (Agentic LLM) · OpenAI Whisper (STT) · ElevenLabs/OpenAI TTS |

---

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Problem Statement](#2-problem-statement)
3. [Goals & Non-Goals](#3-goals--non-goals)
4. [Base Project Recap](#4-base-project-recap)
5. [Feature: Agentic AI Booking Assistant](#5-feature-agentic-ai-booking-assistant)
6. [Interaction Mode 1 — Chat](#6-interaction-mode-1--chat)
7. [Interaction Mode 2 — Voice Call](#7-interaction-mode-2--voice-call)
8. [Agentic AI — Tool Calling Architecture](#8-agentic-ai--tool-calling-architecture)
9. [Design Patterns & Architecture](#9-design-patterns--architecture)
10. [System Architecture — Unified View](#10-system-architecture--unified-view)
11. [Conversation Flow — State Machine](#11-conversation-flow--state-machine)
12. [REST & WebSocket API Contracts](#12-rest--websocket-api-contracts)
13. [AI Tool Definitions (Function Calling)](#13-ai-tool-definitions-function-calling) — tool-by-tool, grouped by phase
14. [Edge Cases, Error Handling & Resilience](#14-edge-cases-error-handling--resilience) — per-tool edge cases, **provider fallback / model switching**, full JSON walkthrough
15. [Testing Strategy](#15-testing-strategy)
16. [Implementation Timeline](#16-implementation-timeline)
17. [Instructor Delivery Plan — 3 Classes × 3 Hours](#17-instructor-delivery-plan--3-classes--3-hours)
18. [Appendix](#18-appendix) — file tree · dependencies · application.yml · **full tool schemas (§18.5)**

---

## 1. Executive Summary

This PRD defines an **Agentic AI Booking Assistant** for the BookMyShow backend project. Unlike the Splitwise AI features (which were input enhancers), this is a **full autonomous agent** that:

- Has a **multi-turn conversation** with the user (like talking to a real human)
- **Calls tools** (database queries, booking APIs, payment APIs) autonomously
- Handles the **complete booking flow**: search → show options → select seats → payment → confirmation
- Works in **two modes**: text chat and voice call

This teaches students the **Agentic AI pattern** — the most important AI architecture in 2026 — where the LLM doesn't just parse input, it **decides what to do next**, calls tools, processes results, and continues the conversation.

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   SPLITWISE (Project 1)        BOOKMYSHOW (Project 2)          │
│                                                                 │
│   AI = smarter INPUT           AI = autonomous AGENT            │
│   ┌──────┐                     ┌──────────────────────┐        │
│   │Image │→ extract → create   │ User: "Book a movie" │        │
│   │Voice │→ parse   → create   │ Agent: searches DB    │        │
│   └──────┘                     │ Agent: shows options   │        │
│                                │ User: "IMAX 7pm"      │        │
│   One-shot processing          │ Agent: checks seats    │        │
│                                │ Agent: books ticket    │        │
│                                │ Agent: processes pay   │        │
│                                │ Agent: confirms ✅     │        │
│                                └──────────────────────┘        │
│                                                                 │
│                                Multi-turn autonomous agent      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Problem Statement

### Current State
The base BookMyShow project exposes REST APIs:
```
GET  /api/movies?city=Mumbai&language=Hindi
GET  /api/movies/{id}/shows
GET  /api/shows/{id}/seats
POST /api/bookings
POST /api/payments
```

A client (mobile/web app) must:
1. Call search API → parse response → display movies
2. User picks a movie → call shows API → display showtimes
3. User picks a show → call seats API → display seat map
4. User picks seats → call booking API → handle response
5. User enters payment → call payment API → handle success/failure

That's **5+ API calls** orchestrated by the frontend. Every step requires the user to make explicit choices from structured UI.

### Pain Points
| Pain Point                              | Impact                                        |
|-----------------------------------------|-----------------------------------------------|
| Multi-step booking is tedious           | Users abandon mid-flow                       |
| Requires structured UI on every platform| High frontend development cost               |
| No conversational flexibility           | "Show me something fun this weekend" → impossible |
| Accessibility limited                   | Can't book while driving, cooking, etc.       |
| No personalization in discovery         | Every user sees the same list                 |

### Desired State
A user says (via chat or voice):
> *"Hey, I want to watch something good this weekend in Mumbai. Maybe an action movie in IMAX?"*

The AI agent:
1. Searches the database for action movies in Mumbai this weekend
2. Presents options conversationally
3. Guides the user through showtime and seat selection
4. Handles payment
5. Confirms the booking

All through **natural conversation** — no forms, no dropdowns, no structured input.

---

## 3. Goals & Non-Goals

### Goals
- Teach students the **Agentic AI / Tool Calling** pattern
- Build a multi-turn conversational agent using Claude's tool-use API
- Agent autonomously decides which tools to call and when
- Two interaction modes: **chat** (WebSocket) and **voice call** (STT + TTS)
- Agent calls into **existing services** — zero changes to base project
- Human-in-the-loop: agent always **confirms before booking/payment**

### Non-Goals
- Building a real payment gateway (we simulate payment)
- Real-time audio streaming (process complete utterances)
- Frontend UI (Postman + WebSocket client for testing)
- Production-grade session management (in-memory is fine)
- Multi-language support (English only for now)

---

## 4. Base Project Recap

From the BookMyShow PDF, the base project has:

### Models
| Model     | Key Fields                                          |
|-----------|-----------------------------------------------------|
| City      | name, theaters                                      |
| Theater   | name, address, halls, shows                         |
| Hall      | name, seats, shows                                  |
| Seat      | number, type (GOLD/DIAMOND/PLATINUM)                |
| Show      | movie, startTime, duration, language, showSeats      |
| ShowSeat  | seat, status (AVAILABLE/BOOKED), price              |
| Movie     | name, rating, category, languages, shows            |
| Ticket    | amount, seats, show, user, payment, status           |
| Payment   | amount, mode (UPI/CREDIT_CARD/NETBANKING), status   |

### Existing Services (that the agent will call)
| Service          | Key Methods                                                    |
|------------------|----------------------------------------------------------------|
| MovieService     | searchMovies(city, language, category, rating)                 |
| ShowService      | getShows(movieId, city, date), getShowSeats(showId)            |
| BookingService   | createBooking(userId, showId, seatIds), cancelBooking(ticketId)|
| PaymentService   | processPayment(ticketId, paymentMode, amount)                  |
| UserService      | getUser(userId), register()                                    |

---

## 5. Feature: Agentic AI Booking Assistant

### 5.1 What Makes This "Agentic"?

Traditional AI integration (like Splitwise):
```
User Input → LLM processes → Structured Output → App acts on it
```

Agentic AI (BookMyShow):
```
User Input → LLM DECIDES what to do → Calls Tool → Gets Result →
LLM processes result → Decides next step → Calls another Tool →
... (loop continues until task is complete) → Final Response to User
```

The LLM is in the **driver's seat**. It decides:
- Which tools to call (search? check seats? book?)
- What parameters to pass (which city? which movie?)
- When to ask the user for more info vs when to proceed
- When the task is complete

### 5.2 User Stories

| ID    | Story                                                                                     | Priority |
|-------|-------------------------------------------------------------------------------------------|----------|
| AG-1  | As a user, I can have a natural conversation to discover and book movies                  | P0       |
| AG-2  | As a user, the agent searches movies based on my preferences without me using filters     | P0       |
| AG-3  | As a user, the agent shows me available showtimes and lets me pick conversationally        | P0       |
| AG-4  | As a user, the agent shows seat availability and helps me choose seats                    | P0       |
| AG-5  | As a user, the agent handles the booking and payment flow                                 | P0       |
| AG-6  | As a user, the agent asks for confirmation before making any booking or payment           | P0       |
| AG-7  | As a user, I can interact via text chat (WebSocket)                                       | P0       |
| AG-8  | As a user, I can interact via voice call (STT + TTS)                                      | P1       |
| AG-9  | As a user, the agent remembers context within our conversation                            | P0       |
| AG-10 | As a user, I can cancel or modify a booking through the agent                             | P1       |
| AG-11 | As a user, I can ask the agent general questions ("What's the rating of Pushpa 2?")       | P1       |

### 5.3 Example Conversation Flow

```
USER: Hey! I want to watch a movie this weekend in Mumbai.

AGENT: 🎬 Sure! Here's what's playing in Mumbai this weekend:

       1. Pushpa 2: The Rule — Action · Hindi · ⭐ 8.2
       2. Fighter — Action/Thriller · Hindi · ⭐ 7.8  
       3. Dunki — Comedy/Drama · Hindi · ⭐ 7.5
       4. Animal — Action/Drama · Hindi · ⭐ 7.1

       Any of these catch your eye? Or want me to filter by genre/language?

       [TOOL CALLED: searchMovies(city="Mumbai", date="this weekend")]

USER: Pushpa 2 sounds great. When can I watch it?

AGENT: Here are the shows for Pushpa 2 in Mumbai this Saturday:

       🏢 PVR Phoenix Mall
          • 10:30 AM — Hindi (2D)
          • 2:00 PM  — Hindi (IMAX) 🔥
          • 6:30 PM  — Hindi (2D)
          • 9:45 PM  — Hindi (IMAX)

       🏢 INOX Nariman Point
          • 11:00 AM — Hindi (2D)
          • 3:30 PM  — Hindi (2D)
          • 7:00 PM  — Hindi (IMAX)

       Which show works for you?

       [TOOL CALLED: getShows(movieId="pushpa2", city="Mumbai", date="Saturday")]

USER: 2 PM IMAX at PVR sounds perfect. 2 tickets please.

AGENT: Great choice! Here's the seat availability for the 2:00 PM IMAX show
       at PVR Phoenix Mall:

       🟢 Available  🔴 Booked  ⬜ Selected

       PLATINUM (₹800):  🟢🟢🟢🔴🔴🟢🟢🟢
       GOLD (₹500):      🟢🔴🟢🟢🔴🟢🟢🟢🟢🔴
       DIAMOND (₹350):   🟢🟢🟢🟢🔴🟢🔴🟢🟢🟢

       Which type of seats would you prefer? And any specific seat numbers?

       [TOOL CALLED: getShowSeats(showId="show_pvr_2pm_imax")]

USER: 2 Gold seats, somewhere in the middle.

AGENT: I've picked seats G5 and G6 (Gold, center row) for you.

       📋 Booking Summary:
       ┌─────────────────────────────────────┐
       │ Movie:    Pushpa 2: The Rule (IMAX) │
       │ Theater:  PVR Phoenix Mall, Mumbai   │
       │ Show:     Saturday, 2:00 PM          │
       │ Seats:    G5, G6 (Gold)              │
       │ Amount:   ₹500 × 2 = ₹1,000         │
       └─────────────────────────────────────┘

       How would you like to pay?
       • UPI
       • Credit Card
       • Netbanking

       Also, do you have any promo code?

       [TOOL CALLED: createBooking(userId, showId, seatIds=["G5","G6"])]

USER: UPI please. And try code FIRST50.

AGENT: ✅ Promo code FIRST50 applied! You get ₹100 off.

       💳 Payment Summary:
       ┌─────────────────────────────────────┐
       │ Subtotal:  ₹1,000                   │
       │ Discount:  -₹100 (FIRST50)          │
       │ Total:     ₹900                      │
       │ Mode:      UPI                        │
       └─────────────────────────────────────┘

       Shall I proceed with the payment?

       [TOOL CALLED: applyCoupon(code="FIRST50", amount=1000)]

USER: Yes, go ahead!

AGENT: ✅ Payment successful! Your tickets are confirmed.

       🎬 BOOKING CONFIRMATION
       ┌─────────────────────────────────────┐
       │ Ticket ID:  TKT-2026-04-19-001      │
       │ Movie:      Pushpa 2 (IMAX)         │
       │ Theater:    PVR Phoenix Mall         │
       │ Date:       Saturday, Apr 19         │
       │ Time:       2:00 PM                  │
       │ Seats:      G5, G6 (Gold)            │
       │ Amount:     ₹900 (after discount)    │
       │ Payment:    UPI — ✅ Success          │
       └─────────────────────────────────────┘

       Enjoy the movie! 🍿 Anything else I can help with?

       [TOOL CALLED: processPayment(ticketId, mode="UPI", amount=900)]
```

---

## 6. Interaction Mode 1 — Chat (WebSocket)

### Why WebSocket, Not REST?

Multi-turn conversation needs **persistent, bidirectional** communication. REST is request-response — the agent can't "push" follow-up messages or stream responses. WebSocket keeps a connection open for the entire conversation session.

### Chat API Flow

```
Client                          Server (WebSocket)              AgentService
  │                                │                                │
  │  WS CONNECT /ws/agent?userId=u1                                │
  │───────────────────────────────▶│                                │
  │                                │  Create session               │
  │  ◀──── "Hi! I'm your booking  │  Initialize conversation      │
  │         assistant. How can I   │  history = []                 │
  │         help?"                 │                                │
  │                                │                                │
  │  "I want to watch a movie     │                                │
  │   in Mumbai"                   │                                │
  │───────────────────────────────▶│  processMessage()             │
  │                                │───────────────────────────────▶│
  │                                │                                │
  │                                │  LLM decides: call searchMovies
  │                                │  tool_use → searchMovies(Mumbai)
  │                                │  gets results                 │
  │                                │  LLM generates response       │
  │                                │                                │
  │  ◀──── "Here's what's playing │  ◀──── agent response         │
  │         in Mumbai..."          │                                │
  │                                │                                │
  │  "Book Pushpa 2, 2 PM IMAX"   │                                │
  │───────────────────────────────▶│  processMessage()             │
  │                                │───────────────────────────────▶│
  │                                │  LLM decides: call getShows   │
  │                                │  then call getShowSeats       │
  │                                │  then call createBooking      │
  │                                │  (multiple tool calls in loop)│
  │                                │                                │
  │  ◀──── "Here are the seats..."│                                │
  │                                │                                │
  ... (conversation continues) ... │                                │
  │                                │                                │
  │  WS DISCONNECT                 │                                │
  │───────────────────────────────▶│  Clean up session             │
```

### WebSocket Message Format

**Client → Server:**
```json
{
  "type": "USER_MESSAGE",
  "sessionId": "sess_abc123",
  "content": "I want to watch a movie in Mumbai"
}
```

**Server → Client (agent response):**
```json
{
  "type": "AGENT_MESSAGE",
  "sessionId": "sess_abc123",
  "content": "Here's what's playing in Mumbai this weekend:\n\n1. Pushpa 2...",
  "toolsCalled": ["searchMovies"],
  "timestamp": "2026-04-19T14:30:00"
}
```

**Server → Client (agent thinking — optional streaming):**
```json
{
  "type": "AGENT_THINKING",
  "sessionId": "sess_abc123",
  "content": "Searching for movies in Mumbai..."
}
```

---

## 7. Interaction Mode 2 — Voice Call

### How It Works

Voice mode wraps the same agent with STT (input) and TTS (output):

```
┌──────────────────────────────────────────────────────────────────┐
│                     VOICE CALL MODE                              │
│                                                                  │
│  User speaks       STT              SAME AGENT         TTS      │
│  ┌─────────┐   ┌──────────┐    ┌──────────────┐   ┌─────────┐ │
│  │ "Book a │──▶│ Whisper  │──▶ │  AgentService │──▶│ TTS API │ │
│  │  movie" │   │ API      │    │  (identical   │   │(ElevenLabs│
│  └─────────┘   │          │    │   to chat)    │   │ /OpenAI) │ │
│                 │ audio →  │    │              │   │          │ │
│  User hears    │ text     │    │ text output   │   │ text →   │ │
│  ┌─────────┐   └──────────┘    └──────────────┘   │ audio    │ │
│  │ "Here   │◀──────────────────────────────────────│          │ │
│  │  are the│                                       └─────────┘ │
│  │  movies"│                                                    │
│  └─────────┘                                                    │
│                                                                  │
│  KEY: The agent logic is IDENTICAL for chat and voice.          │
│  Voice just adds STT before and TTS after.                      │
└──────────────────────────────────────────────────────────────────┘
```

### Voice API Flow

**Start a voice session:**
```
POST /api/agent/voice/start?userId=u1
→ 200 { "sessionId": "sess_voice_xyz" }
```

**Send audio turn:**
```
POST /api/agent/voice/turn
Content-Type: multipart/form-data

sessionId: sess_voice_xyz
audio: <audio_clip.webm>

→ 200 {
    "transcript": "I want to watch Pushpa 2 in IMAX",
    "agentResponse": "Great choice! Here are the IMAX shows...",
    "audioUrl": "/api/agent/voice/audio/resp_123.mp3"
  }
```

**End voice session:**
```
POST /api/agent/voice/end
{ "sessionId": "sess_voice_xyz" }
```

---

## 8. Agentic AI — Tool Calling Architecture

### 8.1 How Claude Tool Calling Works

Claude doesn't just generate text — it can **decide to call functions** (tools) and use the results to continue its response.

```
┌──────────────────────────────────────────────────────────────┐
│                   AGENTIC TOOL CALLING LOOP                  │
│                                                              │
│   ┌───────────────┐                                         │
│   │ User Message   │                                         │
│   │ + History      │                                         │
│   │ + Tool Defs    │                                         │
│   └───────┬───────┘                                         │
│           │                                                  │
│           ▼                                                  │
│   ┌───────────────┐     ┌──────────────┐                    │
│   │   Claude LLM  │────▶│ Response has │──── NO ───┐        │
│   │               │     │ tool_use?    │           │        │
│   └───────────────┘     └──────┬───────┘           │        │
│                                │                    │        │
│                               YES                   │        │
│                                │                    │        │
│                        ┌───────▼───────┐           │        │
│                        │ Execute Tool  │           │        │
│                        │ (call DB /    │           │        │
│                        │  service)     │           │        │
│                        └───────┬───────┘           │        │
│                                │                    │        │
│                        ┌───────▼───────┐           │        │
│                        │ Append tool   │           │        │
│                        │ result to     │           │        │
│                        │ conversation  │           │        │
│                        └───────┬───────┘           │        │
│                                │                    │        │
│                                └──── LOOP BACK ─────┘        │
│                                      to Claude              │
│                                                              │
│                                              ┌──────────┐   │
│                                              │ Return   │   │
│                                              │ final    │   │
│                                              │ text to  │   │
│                                              │ user     │   │
│                                              └──────────┘   │
│                                                              │
│   KEY: The loop continues until Claude returns a text        │
│   response WITHOUT tool calls. Claude decides when to stop.  │
└──────────────────────────────────────────────────────────────┘
```

### 8.2 Tools the Agent Has Access To

| Tool Name              | Description                                        | Maps To                          |
|------------------------|----------------------------------------------------|----------------------------------|
| `search_movies`        | Search movies by city, language, genre, rating     | MovieService.searchMovies()      |
| `get_movie_details`    | Get details of a specific movie                    | MovieService.getMovie()          |
| `get_shows`            | Get showtimes for a movie in a city                | ShowService.getShows()           |
| `get_seat_availability`| Get available seats for a show                     | ShowService.getShowSeats()       |
| `create_booking`       | Book specific seats for a show                     | BookingService.createBooking()   |
| `apply_coupon`         | Validate and apply a promo code                    | CouponService.applyCoupon()      |
| `process_payment`      | Process payment for a booking                      | PaymentService.processPayment()  |
| `cancel_booking`       | Cancel an existing booking                         | BookingService.cancelBooking()   |
| `get_booking_details`  | Get details of an existing booking                 | BookingService.getBooking()      |
| `get_user_bookings`    | Get all bookings for the current user              | BookingService.getUserBookings() |

### 8.3 Tool Definitions Sent to Claude

```json
{
  "tools": [
    {
      "name": "search_movies",
      "description": "Search for currently showing movies. Use this when the user wants to find or browse movies. You can filter by city, language, genre/category, and minimum rating.",
      "input_schema": {
        "type": "object",
        "properties": {
          "city": { "type": "string", "description": "City name (e.g., Mumbai, Delhi, Bangalore)" },
          "language": { "type": "string", "description": "Movie language (e.g., Hindi, English, Tamil)" },
          "category": { "type": "string", "description": "Genre (e.g., Action, Comedy, Drama, Thriller)" },
          "min_rating": { "type": "number", "description": "Minimum rating filter (1-10)" }
        },
        "required": ["city"]
      }
    },
    {
      "name": "get_shows",
      "description": "Get available showtimes for a specific movie in a city. Use after the user has chosen a movie.",
      "input_schema": {
        "type": "object",
        "properties": {
          "movie_id": { "type": "string" },
          "city": { "type": "string" },
          "date": { "type": "string", "description": "Date in YYYY-MM-DD format" }
        },
        "required": ["movie_id", "city"]
      }
    },
    {
      "name": "get_seat_availability",
      "description": "Get available seats for a specific show. Use after the user has chosen a showtime.",
      "input_schema": {
        "type": "object",
        "properties": {
          "show_id": { "type": "string" }
        },
        "required": ["show_id"]
      }
    },
    {
      "name": "create_booking",
      "description": "Book seats for a show. ALWAYS confirm with the user before calling this. This action cannot be undone easily.",
      "input_schema": {
        "type": "object",
        "properties": {
          "user_id": { "type": "string" },
          "show_id": { "type": "string" },
          "seat_ids": { "type": "array", "items": { "type": "string" } }
        },
        "required": ["user_id", "show_id", "seat_ids"]
      }
    },
    {
      "name": "apply_coupon",
      "description": "Validate and apply a coupon/promo code to a booking amount.",
      "input_schema": {
        "type": "object",
        "properties": {
          "coupon_code": { "type": "string" },
          "amount": { "type": "number" },
          "ticket_id": { "type": "string" }
        },
        "required": ["coupon_code", "amount"]
      }
    },
    {
      "name": "process_payment",
      "description": "Process payment for a booking. ALWAYS confirm amount and mode with user first.",
      "input_schema": {
        "type": "object",
        "properties": {
          "ticket_id": { "type": "string" },
          "payment_mode": { "type": "string", "enum": ["UPI", "CREDIT_CARD", "NETBANKING"] },
          "amount": { "type": "number" }
        },
        "required": ["ticket_id", "payment_mode", "amount"]
      }
    },
    {
      "name": "cancel_booking",
      "description": "Cancel an existing booking. Can only cancel if more than 1 hour before showtime.",
      "input_schema": {
        "type": "object",
        "properties": {
          "ticket_id": { "type": "string" }
        },
        "required": ["ticket_id"]
      }
    },
    {
      "name": "get_booking_details",
      "description": "Get details of a specific booking by ticket ID.",
      "input_schema": {
        "type": "object",
        "properties": {
          "ticket_id": { "type": "string" }
        },
        "required": ["ticket_id"]
      }
    },
    {
      "name": "get_user_bookings",
      "description": "Get all bookings for the current user.",
      "input_schema": {
        "type": "object",
        "properties": {
          "user_id": { "type": "string" }
        },
        "required": ["user_id"]
      }
    }
  ]
}
```

---

## 9. Design Patterns & Architecture

### 9.1 Patterns Used

This project uses **3 design patterns** — different from Splitwise to teach variety:

| Pattern              | Where Used                         | What It Does                                         |
|----------------------|------------------------------------|------------------------------------------------------|
| **Adapter Pattern**  | AI Provider abstraction            | Swap LLM/STT/TTS providers via config (same as P1)  |
| **Command Pattern**  | Tool execution                     | Each tool is a Command object — agent picks & runs   |
| **State Pattern**    | Conversation flow management       | Tracks where the user is in the booking journey      |

### 9.2 Pattern 1: Adapter Pattern (AI Providers)

Same as Splitwise — interface per AI capability, swap via `application.yml`.

```
AgentService
    │
    ├── LLMAdapter (interface)
    │   ├── ClaudeAgentAdapter  ← @ConditionalOnProperty("ai.llm.provider=claude")
    │   └── OpenAIAgentAdapter  ← @ConditionalOnProperty("ai.llm.provider=openai")
    │
    ├── STTAdapter (interface)           ← reusable from Project 1!
    │   ├── WhisperSTTAdapter
    │   └── DeepgramSTTAdapter
    │
    └── TTSAdapter (interface)           ← NEW for voice output
        ├── ElevenLabsTTSAdapter
        └── OpenAITTSAdapter
```

### 9.3 Pattern 2: Command Pattern (Tool Execution)

**Problem:** The agent has 9+ tools. When Claude returns `tool_use: "search_movies"`, we need to route it to the right service method. We don't want a giant if-else chain.

**Solution:** Each tool is a `ToolCommand` object registered in a map. The agent executor looks up the tool name and calls `execute()`.

```
                    ┌───────────────────────┐
                    │   «interface»         │
                    │   ToolCommand         │
                    ├───────────────────────┤
                    │ + getName(): String   │
                    │ + getSchema(): JSON   │
                    │ + execute(            │
                    │     params: Map       │
                    │   ): ToolResult       │
                    └───────────┬───────────┘
                                │
        ┌───────────┬───────────┼───────────┬────────────┐
        │           │           │           │            │
  ┌─────▼────┐ ┌────▼─────┐ ┌──▼───────┐ ┌▼─────────┐ ┌▼──────────┐
  │ Search   │ │ GetShows │ │ GetSeats │ │ Create   │ │ Process   │
  │ Movies   │ │ Command  │ │ Command  │ │ Booking  │ │ Payment   │
  │ Command  │ │          │ │          │ │ Command  │ │ Command   │
  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └───────────┘

  Each command wraps an existing @Service method.
  New tool = new Command class. Zero changes elsewhere.
```

```java
// Command interface
public interface ToolCommand {
    String getName();          // "search_movies"
    Object getInputSchema();   // JSON schema for Claude
    ToolResult execute(Map<String, Object> params);
}

// Example implementation
@Component
public class SearchMoviesCommand implements ToolCommand {

    private final MovieService movieService;  // existing service

    @Override
    public String getName() { return "search_movies"; }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String city = (String) params.get("city");
        String language = (String) params.get("language");
        String category = (String) params.get("category");

        List<Movie> movies = movieService.searchMovies(city, language, category, null);
        return ToolResult.success(movies);
    }
}

// Registry — auto-collects all commands
@Component
public class ToolRegistry {

    private final Map<String, ToolCommand> tools;

    public ToolRegistry(List<ToolCommand> allCommands) {
        this.tools = allCommands.stream()
            .collect(Collectors.toMap(ToolCommand::getName, Function.identity()));
    }

    public ToolCommand getTool(String name) { return tools.get(name); }
    public List<Object> getAllSchemas() { ... }  // sent to Claude
}
```

**Why Command Pattern here (vs. if-else)?**
> *"We have 9 tools today. Next month someone adds 'get_reviews' and 'share_ticket'. With Command pattern: add one class, done. With if-else: modify the switch statement, risk breaking existing tools."*

### 9.4 Pattern 3: State Pattern (Conversation Flow)

**Problem:** The booking flow has states (browsing → selected movie → selected show → selected seats → payment → confirmed). The agent should behave differently based on state (e.g., don't process payment if no booking exists).

**Solution:** Track conversation state. The LLM handles most transitions naturally, but we add guardrails.

```
                    ┌─────────────┐
                    │   IDLE      │
                    │ (greeting)  │
                    └──────┬──────┘
                           │ user asks about movies
                           ▼
                    ┌─────────────┐
                    │  BROWSING   │
                    │ (searching) │
                    └──────┬──────┘
                           │ user picks a movie
                           ▼
                    ┌─────────────┐
                    │  SELECTING  │
                    │  SHOW       │
                    └──────┬──────┘
                           │ user picks a show
                           ▼
                    ┌─────────────┐
                    │  SELECTING  │
                    │  SEATS      │
                    └──────┬──────┘
                           │ user picks seats
                           ▼
                    ┌─────────────┐
                    │  BOOKING    │
                    │  CONFIRM    │◀──── user says "yes"
                    └──────┬──────┘
                           │ confirmed
                           ▼
                    ┌─────────────┐
                    │  PAYMENT    │
                    └──────┬──────┘
                           │ payment success
                           ▼
                    ┌─────────────┐
                    │  COMPLETED  │
                    └─────────────┘
```

```java
public enum ConversationState {
    IDLE,
    BROWSING,
    SELECTING_SHOW,
    SELECTING_SEATS,
    BOOKING_CONFIRM,
    PAYMENT,
    COMPLETED
}
```

**Important:** The state is a **guardrail**, not a rigid controller. The LLM can handle most transitions naturally. The state prevents dangerous operations (e.g., `process_payment` should only execute if state is PAYMENT).

---

## 10. System Architecture — Unified View

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      SPRING BOOT APPLICATION                             │
│                                                                          │
│  ┌─── Input Channels ───────────────────────────────────────────────┐   │
│  │                                                                    │   │
│  │  CHAT (WebSocket)              VOICE (REST)                       │   │
│  │  ┌─────────────────────┐      ┌──────────────────────────┐       │   │
│  │  │ @ServerEndpoint     │      │ @RestController          │       │   │
│  │  │ AgentWebSocket      │      │ VoiceAgentController     │       │   │
│  │  │ Handler             │      │                          │       │   │
│  │  │                     │      │ POST /voice/start        │       │   │
│  │  │ WS /ws/agent        │      │ POST /voice/turn         │       │   │
│  │  │                     │      │ POST /voice/end          │       │   │
│  │  └─────────┬───────────┘      └──────────┬───────────────┘       │   │
│  │            │                              │                       │   │
│  │            │ text                         │ audio                 │   │
│  │            │                    ┌─────────▼──────────┐           │   │
│  │            │                    │ STTAdapter         │           │   │
│  │            │                    │ (Whisper)          │           │   │
│  │            │                    │ audio → text       │           │   │
│  │            │                    └─────────┬──────────┘           │   │
│  │            │                              │ text                 │   │
│  │            └──────────┬───────────────────┘                      │   │
│  │                       │                                          │   │
│  └───────────────────────┼──────────────────────────────────────────┘   │
│                          ▼                                              │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                     AGENT SERVICE (the brain)                      │ │
│  │                                                                    │ │
│  │  ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐  │ │
│  │  │ Session      │   │ Conversation     │   │ LLMAdapter       │  │ │
│  │  │ Manager      │   │ History          │   │ (Claude API      │  │ │
│  │  │              │   │                  │   │  with tools)     │  │ │
│  │  │ sessionId →  │   │ List<Message>    │   │                  │  │ │
│  │  │ {state,      │   │ (user + agent +  │   │ sendWithTools()  │  │ │
│  │  │  history,    │   │  tool_use +      │   │ → tool_use or    │  │ │
│  │  │  userId}     │   │  tool_result)    │   │   text response  │  │ │
│  │  └──────────────┘   └──────────────────┘   └────────┬─────────┘  │ │
│  │                                                      │            │ │
│  │                                              ┌───────▼────────┐   │ │
│  │                                              │ AGENTIC LOOP   │   │ │
│  │                                              │                │   │ │
│  │                                              │ while(tool_use)│   │ │
│  │                                              │   execute tool │   │ │
│  │                                              │   append result│   │ │
│  │                                              │   call LLM     │   │ │
│  │                                              │ return text    │   │ │
│  │                                              └───────┬────────┘   │ │
│  │                                                      │            │ │
│  └──────────────────────────────────────────────────────┼────────────┘ │
│                                                         │              │
│  ┌──────────────────────────────────────────────────────▼────────────┐ │
│  │                      TOOL REGISTRY (Command Pattern)              │ │
│  │                                                                    │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐            │ │
│  │  │ Search   │ │ GetShows │ │ GetSeats │ │ Create   │ ...        │ │
│  │  │ Movies   │ │ Command  │ │ Command  │ │ Booking  │            │ │
│  │  │ Command  │ │          │ │          │ │ Command  │            │ │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘            │ │
│  │       │            │            │            │                   │ │
│  └───────┼────────────┼────────────┼────────────┼───────────────────┘ │
│          │            │            │            │                     │
│  ┌───────▼────────────▼────────────▼────────────▼───────────────────┐ │
│  │                  EXISTING SERVICES (untouched)                    │ │
│  │                                                                    │ │
│  │  MovieService  ShowService  BookingService  PaymentService        │ │
│  │  UserService   CouponService                                      │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    VOICE OUTPUT (for voice mode only)              │ │
│  │                                                                    │ │
│  │  Agent text response → TTSAdapter → audio file → returned to user │ │
│  │                        (ElevenLabs / OpenAI TTS)                   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    application.yml                                 │ │
│  │  ai.llm.provider: claude                                          │ │
│  │  ai.stt.provider: whisper                                         │ │
│  │  ai.tts.provider: elevenlabs                                      │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Conversation Flow — State Machine

### 11.1 System Prompt for the Agent

```
You are a friendly movie booking assistant for BookMyShow. You help users 
discover movies, choose showtimes, select seats, and complete bookings.

PERSONALITY:
- Warm, enthusiastic about movies, concise
- Use emojis sparingly (🎬 🍿 ✅)  
- Present options in numbered lists for easy selection
- Be proactive: suggest popular/trending movies when browsing

RULES:
1. ALWAYS confirm with the user before calling create_booking or process_payment
2. If the user hasn't specified a city, ask for it before searching
3. When showing seats, indicate pricing for each type
4. If a show is within 1 hour, warn that booking may not be cancellable
5. After successful booking, show a clear confirmation summary
6. If payment fails, offer to retry or try a different method
7. You can handle multiple tool calls in sequence to fulfill a request
8. Keep conversation context — don't re-ask things the user already told you

CONTEXT:
- Current user ID: {userId}
- Current city (if known): {city}
- Current date/time: {now}
```

### 11.2 State Guardrails

```java
@Component
public class ToolGuardService {

    public void validateToolCall(String toolName, ConversationState state) {
        switch (toolName) {
            case "create_booking":
                if (state.ordinal() < SELECTING_SEATS.ordinal())
                    throw new PrematureToolCallException(
                        "Cannot book without selecting seats first");
                break;
            case "process_payment":
                if (state != BOOKING_CONFIRM && state != PAYMENT)
                    throw new PrematureToolCallException(
                        "Cannot process payment without an active booking");
                break;
            case "cancel_booking":
                // Allowed in any state if ticket exists
                break;
            default:
                // search, get_shows, get_seats — allowed anytime
                break;
        }
    }
}
```

---

## 12. REST & WebSocket API Contracts

### 12.1 WebSocket — Chat Mode

| Endpoint          | Description                     |
|-------------------|---------------------------------|
| `WS /ws/agent`    | WebSocket connection for chat   |

**Query params:** `userId=u1`

**Message types (Client → Server):**
```json
{ "type": "USER_MESSAGE", "content": "string" }
```

**Message types (Server → Client):**
```json
{ "type": "AGENT_MESSAGE", "content": "string", "toolsCalled": ["string"], "state": "string" }
{ "type": "AGENT_THINKING", "content": "Searching for movies..." }
{ "type": "ERROR", "content": "Something went wrong", "code": "string" }
{ "type": "SESSION_STARTED", "sessionId": "string" }
```

### 12.2 REST — Voice Mode

| Method | Endpoint                 | Description                      | Request                  | Response                    |
|--------|--------------------------|----------------------------------|--------------------------|-----------------------------|
| POST   | `/api/agent/voice/start` | Start voice session              | `?userId=u1`             | `{ sessionId }`             |
| POST   | `/api/agent/voice/turn`  | Send audio, get response + audio | multipart (sessionId, audio) | `{ transcript, response, audioUrl }` |
| POST   | `/api/agent/voice/end`   | End voice session                | `{ sessionId }`          | `{ summary }`               |
| GET    | `/api/agent/voice/audio/{id}` | Download TTS audio file     | —                        | `audio/mpeg`                |

### 12.3 REST — Chat Fallback (for Postman testing without WebSocket)

| Method | Endpoint                | Description                      | Request                  | Response                    |
|--------|-------------------------|----------------------------------|--------------------------|-----------------------------|
| POST   | `/api/agent/chat/start` | Start a chat session             | `{ userId }`             | `{ sessionId, greeting }`   |
| POST   | `/api/agent/chat/send`  | Send a message                   | `{ sessionId, message }` | `{ response, toolsCalled }` |
| POST   | `/api/agent/chat/end`   | End session                      | `{ sessionId }`          | `{ summary }`               |

---

## 13. AI Tool Definitions (Function Calling)

### 13.1 What Exactly Is a "Tool Call"? (Plain-Backend Explanation)

Forget AI for a second. In plain backend terms, calling an LLM with tools is just a REST round-trip with one twist:

```
Our server  ──POST──▶  Claude API   ( body = { system, messages, tools[] } )

Claude replies JSON with a field called "stop_reason". Two values we care about:

   stop_reason = "end_turn"    →  Claude has a final text answer. Return it to the user.
   stop_reason = "tool_use"    →  Claude wants us to call a function. It also sends:
                                    { name: "search_movies", input: { city: "Mumbai" } }

If tool_use:
   1. Look up "search_movies" in our Map<String, ToolCommand>
   2. Call cmd.execute(input)   → returns JSON result
   3. Append both Claude's "tool_use" AND our result ("tool_result") to history
   4. POST to Claude AGAIN with the updated history
   5. Claude sees the result and either answers ("end_turn") or asks for another tool

Loop until "end_turn".
```

That's the whole mechanism. No ML magic. **Claude is a REST service that replies either "here's your answer" or "please run this function and show me the result."** Our job is:
1. Expose a catalogue of functions it can choose from.
2. Execute whatever it picks. Feed the result back. Loop.

### 13.2 The Agentic Loop — Step by Step

```
1. User sends message ──────▶  AgentService.processMessage(sessionId, "...")

2. Fetch session (history + state + userId)

3. Append user message to history

4. LOOP (hard cap: 10 iterations — else abort):
   a. Call llmAdapter.sendWithTools(systemPrompt, history, toolSchemas)
   b. If response = TEXT → append to history → return to user → BREAK
   c. If response = TOOL_USE:
      - Validate: ToolGuardService checks this tool is allowed in the current state
      - Lookup:   ToolRegistry.get(name)  — null? → return "unknown tool" as tool_result
      - Execute: cmd.execute(params)  → returns ToolResult (success or error)
      - Append:  assistant's tool_use block + user's tool_result block to history
      - CONTINUE loop (Claude will see the tool_result and decide the next step)

5. If loop hits 10 iterations → return "Sorry, I got stuck. Can we try again?"
```

### 13.3 The 10 Tools — Grouped by Booking Phase

We build tools in **4 phases**, safest (read-only) first. Students implement phase-by-phase so the agent actually *does* something halfway through the class.

#### Phase 1 — Discovery (read-only, zero side effects, implement FIRST)

| Tool | Purpose | Params (LLM sends) | Returns (we send back) |
|---|---|---|---|
| `search_movies` | Find movies playing in a city | `city` (req), `language?`, `category?`, `min_rating?` | `[{movieId, name, rating, category, languages, durationMin}]` |
| `get_movie_details` | Synopsis/rating/cast for one movie | `movieId` | `{name, rating, synopsis, cast[], languages, duration}` |
| `get_shows` | Showtimes for a movie in a city on a date | `movieId`, `city`, `date?` (YYYY-MM-DD) | `[{showId, theaterName, hallName, startTime, language, format, availableSeatCount, minPrice}]` |
| `get_seat_availability` | Seat map + prices for a specific show | `showId` | `{GOLD:[{seatId,row,num,price,status}], DIAMOND:[...], PLATINUM:[...]}` |

#### Phase 2 — Preview (read-only, but uses user context)

| Tool | Purpose | Params | Returns |
|---|---|---|---|
| `preview_price` | Calculate what a booking will cost (before locking seats) | `showId`, `seatIds[]`, `couponCode?` | `{subtotal, discount, total, breakdown:[{seatId, price}]}` |
| `apply_coupon` | Validate a coupon code — does NOT commit it | `couponCode`, `amount`, `userId` | `{valid, discount, finalAmount, reason?}` |

#### Phase 3 — Write actions (irreversible — agent MUST confirm first)

| Tool | Purpose | Params | Returns |
|---|---|---|---|
| `create_booking` | Lock seats + create a **PENDING** ticket (5-min hold) | `userId`, `showId`, `seatIds[]` | `{ticketId, amount, heldUntil, idempotencyKey}` |
| `process_payment` | Charge for a pending booking | `ticketId`, `paymentMode`, `amount`, `idempotencyKey` | `{paymentId, status, message}` |

> **Why PENDING-with-hold instead of instant book?**
> A PENDING ticket locks the seats for 5 minutes so nobody else can grab them while the user decides about payment. If payment never happens, the hold expires and the seats go back. This matches real BookMyShow and cleanly handles "user walks away mid-flow."

#### Phase 4 — Post-booking

| Tool | Purpose | Params | Returns |
|---|---|---|---|
| `get_user_bookings` | List the user's bookings | `userId`, `status?` | `[{ticketId, movieName, showTime, seatCount, amount, status}]` |
| `get_booking_details` | Full details of one booking | `ticketId` | full ticket JSON |
| `cancel_booking` | Cancel a booking (subject to 1-hr cutoff) | `ticketId`, `userId` | `{status, refundAmount, message}` |

### 13.4 Tool Schema Sent to Claude

Every tool above turns into one JSON schema entry in the `tools` array we POST to Claude:

```json
{
  "name": "search_movies",
  "description": "Search for currently playing movies in a city. Use this when the user asks about movies, wants recommendations, or hasn't specified a movie yet. If the user hasn't named a city, ASK first — do not guess a city.",
  "input_schema": {
    "type": "object",
    "properties": {
      "city":       { "type": "string", "description": "City name, e.g. Mumbai, Delhi" },
      "language":   { "type": "string", "description": "Hindi | English | Tamil | ..." },
      "category":   { "type": "string", "description": "Action | Comedy | Drama | Thriller | ..." },
      "min_rating": { "type": "number", "description": "Minimum rating (1-10)" }
    },
    "required": ["city"]
  }
}
```

> **Golden rule:** the `description` field is the LLM's only documentation for that tool. A bad description → Claude picks the wrong tool or skips it entirely. Always say *when* to use it, not just *what* it does.

(Full schemas for all 10 tools in Appendix §18.5.)

### 13.5 The AgentService Loop in Java

```java
@Service
public class AgentService {

    private static final int MAX_TOOL_CALLS_PER_TURN = 10;

    private final LLMAdapter llmAdapter;            // wraps Claude / OpenAI / Gemini
    private final ToolRegistry toolRegistry;        // all 10 ToolCommands
    private final ToolGuardService guardService;    // state-based safety checks
    private final SessionManager sessionManager;

    public String processMessage(String sessionId, String userMessage) {
        Session session = sessionManager.getSession(sessionId);
        session.addMessage(Role.USER, userMessage);

        for (int iter = 0; iter < MAX_TOOL_CALLS_PER_TURN; iter++) {

            LLMResponse resp = llmAdapter.sendWithTools(
                session.getSystemPrompt(),
                session.getHistory(),
                toolRegistry.getAllSchemas()
            );

            // Case 1 — Claude gave a text answer: we're done
            if (resp.isTextResponse()) {
                session.addMessage(Role.ASSISTANT, resp.getText());
                return resp.getText();
            }

            // Case 2 — Claude wants to call a tool
            ToolCall call = resp.getToolCall();
            ToolResult result;

            try {
                guardService.validateToolCall(call.getName(), session.getState());
                ToolCommand cmd = toolRegistry.getTool(call.getName());
                if (cmd == null) {
                    result = ToolResult.error("Unknown tool: " + call.getName());
                } else {
                    result = cmd.execute(call.getParams());   // returns success OR error as DATA
                }
                session.updateState(call.getName());
            } catch (Exception e) {
                result = ToolResult.error(e.getMessage());    // errors flow back as data, not exceptions
            }

            session.addAssistantToolUse(resp.getRaw());
            session.addToolResult(call.getId(), result);
            // loop continues — Claude will process the tool_result and decide what's next
        }

        // Hit the cap — something is wrong
        return "I'm having trouble completing this request. Could we try again?";
    }
}
```

### 13.6 LLMAdapter Interface (Provider-Agnostic)

```java
public interface LLMAdapter {
    String getName();                    // "claude", "openai", "gemini"
    LLMResponse sendWithTools(
        String systemPrompt,
        List<Message> history,           // our canonical format (§14.4.3)
        List<Object> toolSchemas
    );
}
```

Each adapter internally converts `history` to its provider's wire format and converts the response back to a common `LLMResponse`. This is what makes provider-switching possible (see §14.4).

### 13.7 TTSAdapter Interface (for Voice Output)

```java
public interface TTSAdapter {
    byte[] synthesize(String text, String voice);
}
```

Implementations: `OpenAITTSAdapter` (POST `/v1/audio/speech`), `ElevenLabsTTSAdapter`. Returns MP3 bytes that the controller serves to the client.

---

## 14. Edge Cases, Error Handling & Resilience

### 14.1 Three Layers of Things That Can Go Wrong

1. **Tool-level failures** — one tool call fails (seats taken, payment declined, bad params).
2. **Loop-level failures** — the agent itself misbehaves (infinite loop, hallucinated tool name).
3. **Provider-level failures** — the LLM provider is down / slow / rate-limited.

Each layer has a different recovery strategy. We teach students to think in these three layers.

### 14.2 Layer 1 — Edge Cases Per Tool (with handling)

**Core principle: tools return errors as DATA, not as thrown exceptions.** The LLM must *see* the error so it can recover. In every `execute()` method:
```java
return ToolResult.error("seats already booked");   // ✅ LLM can react
// throw new RuntimeException(...)                  // ❌ LLM sees nothing, agent crashes
```

| Tool | Edge case | How we handle it |
|---|---|---|
| `search_movies` | User didn't give a city | Return `{error: "city required"}`. System prompt instructs Claude: *"Always ask for city first, never guess."* |
| `search_movies` | Typo: "mumbay" | Return empty list + `knownCities:["Mumbai","Delhi",...]`. Agent: *"Did you mean Mumbai?"* |
| `search_movies` | Valid city, no movies match filters | Empty list. Agent suggests dropping a filter. |
| `get_movie_details` | Invalid movieId | Return `{error: "movie not found"}`. Agent re-searches. |
| `get_shows` | No shows on requested date | Empty list + `nextAvailableDate`. Agent offers the next date. |
| `get_shows` | Show in the past | Filter out server-side. Never returned to LLM. |
| `get_seat_availability` | Show already started | Return `{error: "show started"}`. Agent apologises, suggests next show. |
| `get_seat_availability` | All seats booked | Return empty seat groups. Agent suggests a different showtime. |
| `preview_price` | Seat IDs don't belong to that show | Validate server-side. Return `{error: "invalid seats"}`. |
| `create_booking` | **Race: two sessions book seat G5 at once** | `synchronized` block in `BookingService`. Loser gets `SeatAlreadyBookedException` → tool returns `{takenSeats:["G5"]}`. Agent: *"G5 just got taken — want G6 instead?"* |
| `create_booking` | Past 1-hour cutoff | `CutoffExceededException` → `{error: "too close to showtime"}`. |
| `create_booking` | Seat doesn't exist in that show | Validate params — never trust LLM input. Return `{error: "invalid seat ids"}`. |
| `create_booking` | User spams the tool (duplicate bookings) | Idempotency key per `userId + showId + seatIds` — duplicate call returns the same PENDING ticket. |
| `process_payment` | Payment declined (we simulate ~10% random fail) | Return `{status:"FAILED", reason}`. Agent offers retry or a different payment mode. |
| `process_payment` | **Network flake → agent retries** | `idempotencyKey` is REQUIRED. Same key = same outcome. Never double-charge. |
| `process_payment` | LLM sends wrong amount | Recompute amount from `ticketId` server-side. Ignore the LLM's number entirely. |
| `process_payment` | Ticket already paid | Idempotent — return existing payment status. |
| `apply_coupon` | Invalid / expired / already used | `{valid:false, reason:"..."}`. Never throw — it's a regular outcome. |
| `cancel_booking` | Not owned by user | Forbidden. Check `ticket.userId == sessionUserId`. |
| `cancel_booking` | Already cancelled | Idempotent — return current status. |
| `cancel_booking` | Past cutoff | `{error: "too close to showtime to cancel"}`. |

### 14.3 Layer 2 — Loop-Level Edge Cases

| What goes wrong | How we handle it |
|---|---|
| LLM hallucinates a tool name (`list_movies_in_mumbai`) | `ToolRegistry.get(name)` returns null → tool_result = `{error:"unknown tool"}`. Claude self-corrects on next iteration. |
| LLM passes wrong param type (number where string expected) | Jackson parse error → tool_result = `{error:"invalid params"}`. Claude retries. |
| LLM calls `process_payment` before any booking exists | `ToolGuardService` rejects → tool_result = `{error:"no active booking"}`. Agent redirects user. |
| Infinite loop (tool → tool → tool …) | **Hard cap of 10 tool calls per user turn.** Abort with a generic "Let's try that again" message. |
| Tool result is huge (500 seats returned) | Summarise server-side before sending to LLM: group by seat type, return counts + a few samples. Saves tokens, keeps responses fast. |
| LLM returns malformed JSON | Catch parse error, retry once with same history. If still malformed → abort turn. |

### 14.4 Layer 3 — Provider Fallback & Model Switching

This is the part most tutorials skip. It's the difference between a toy and something that works in a real class demo when the WiFi hiccups.

#### 14.4.1 The Problem

Mid-conversation, the LLM provider fails:
- Timeout (>30s no response)
- HTTP 429 (rate-limited)
- HTTP 5xx (provider outage)
- Garbage response (malformed JSON, refusal, empty content)

We do NOT want to tell the user *"our AI broke, please start over."* We want to silently switch to a backup provider (OpenAI, Gemini) and carry on — **without losing context**.

#### 14.4.2 Key Insight: WE Own the Conversation, Not the Provider

Claude is **stateless**. It doesn't remember the previous turn. Every API call includes the **entire** history from scratch. The history lives in our server's `SessionManager`.

That means switching providers = re-send the same history to a different endpoint. The new provider sees:
- The system prompt
- Every user message
- Every assistant reply (including prior `tool_use` blocks)
- Every `tool_result`

…and continues from there. The new provider has the full picture because **we were the ones carrying it all along.**

```
┌──────────────────────────────────────────────────────────────────┐
│  Claude is STATELESS.  OpenAI is STATELESS. Gemini is STATELESS. │
│                                                                  │
│  Our SessionManager holds the ONLY copy of the conversation.     │
│  Every turn we re-send the whole thing.                          │
│  Switch provider? Just re-send to a different URL.               │
└──────────────────────────────────────────────────────────────────┘
```

#### 14.4.3 Canonical Message Format

We do NOT store messages in Claude's format or OpenAI's format. We store them in **our own canonical format**, and each adapter converts at send-time.

```java
public class Message {
    Role role;                   // USER, ASSISTANT, TOOL_RESULT
    String textContent;          // plain text (nullable)
    ToolUseBlock toolUse;        // if assistant called a tool (nullable)
    ToolResultBlock toolResult;  // if this is a tool result (nullable)
    Instant timestamp;
    String producedByProvider;   // "claude" | "openai" | "gemini"  (for observability)
}
```

Each adapter translates:

```
ClaudeAdapter.toWireFormat(history)  →  Claude's format (tool_use as content block)
OpenAIAdapter.toWireFormat(history)  →  OpenAI's format (tool_calls inside assistant message,
                                                          role:"tool" for results)
GeminiAdapter.toWireFormat(history)  →  Gemini's format (functionCall / functionResponse)
```

Tool schemas are also generated once from our `ToolCommand` interface and converted per provider's expected schema syntax.

#### 14.4.4 The Fallback Chain

Configured in `application.yml`:

```yaml
ai:
  llm:
    primary: claude
    fallbacks: [openai, gemini]
    retry-per-provider: 2
    per-call-timeout-seconds: 30
    total-turn-timeout-seconds: 60
```

`FallbackLLMAdapter` wraps the real adapters:

```java
@Service
@Primary
public class FallbackLLMAdapter implements LLMAdapter {

    private final List<LLMAdapter> chain;          // [claude, openai, gemini] in order

    public LLMResponse sendWithTools(String prompt, List<Message> history, List<Object> tools) {
        Exception lastError = null;

        for (LLMAdapter adapter : chain) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    LLMResponse resp = adapter.sendWithTools(prompt, history, tools);
                    metrics.recordProviderUsed(adapter.getName());
                    return resp;
                } catch (TimeoutException | RateLimitException | ProviderException e) {
                    log.warn("Provider {} attempt {} failed: {}", adapter.getName(), attempt, e.getMessage());
                    lastError = e;
                    // fall through: retry same adapter, or move to next
                }
            }
            log.warn("Giving up on {}, trying next provider", adapter.getName());
        }

        throw new AllProvidersFailedException("All LLM providers failed", lastError);
    }
}
```

#### 14.4.5 Mid-Loop Failover — Three Scenarios

**Scenario A — Claude fails at the START of a turn (before any tool call this turn):**
- History so far: `[system, user_1, assistant_1, user_2]`
- FallbackLLMAdapter tries OpenAI with the same history
- OpenAI responds normally — maybe slightly different wording, but semantically correct
- ✅ User never notices

**Scenario B — Claude succeeds on iteration 1 (returns tool_use), fails on iteration 2:**
- History so far: `[..., user_msg, assistant(tool_use:search_movies), tool_result(movies:[...])]`
- We loop back to call the LLM. Claude fails.
- FallbackLLMAdapter switches to OpenAI, sends the same history.
- OpenAI sees: search was done, movies were found, now decide the next step.
- OpenAI might call `get_shows` next, or respond with text listing the movies. Either is fine — **the tool_result is shared ground truth.**
- ✅ Works cleanly.

**Scenario C — Claude times out WHILE we're awaiting its response:**
- We haven't written anything to history (we never received a response to write).
- Retry with OpenAI using history exactly as it was *before* the failed call.
- OpenAI decides fresh. No inconsistency possible.
- ✅ Because history is **append-only** and we only append on success.

#### 14.4.6 What Makes This Safe

- **Tool reads are idempotent by nature.** Calling `search_movies` twice is harmless.
- **Tool writes use idempotency keys.** If OpenAI re-calls `create_booking` or `process_payment` with the same key, we return the prior result instead of duplicating.
- **Canonical history is append-only.** We never rewrite or delete on failure — we just retry the next call.
- **Each successful provider response is atomic.** Either we got a complete response and appended it, or we didn't append anything.
- **Cross-provider format differences are handled by adapters**, not by the session layer.

#### 14.4.7 Degraded Mode — When ALL Providers Fail

If the whole chain fails:
- Return a canned message: *"I'm having trouble reaching my assistant right now. Your session is saved — please try again in a minute."*
- Persist the session so the user resumes exactly where they left off.
- Log + emit metric for ops.
- Do **not** drop history — next turn it's still there and a retry might succeed.

#### 14.4.8 Tracking Which Provider Answered Each Turn

Each message stores `producedByProvider`. Useful for:
- Debugging ("this weird reply came from Gemini, not Claude")
- Metrics ("claude served 94% of turns, openai 5%, gemini 1%")
- Billing (per-provider token costs)

The user doesn't see this — it's internal observability.

### 14.5 Exception Hierarchy

```
AgentException (RuntimeException)
├── SessionNotFoundException          →  return 404 / prompt reconnect
├── SessionExpiredException           →  return 410 / new session
├── PrematureToolCallException        →  tool_result = error, LLM retries
├── ToolExecutionException            →  tool_result = error, LLM retries
├── UnknownToolException              →  tool_result = error, LLM retries
├── SeatAlreadyBookedException        →  tool_result = error, agent suggests alt
├── CutoffExceededException           →  tool_result = error, agent warns user
├── PaymentFailedException            →  tool_result = error, agent offers retry
├── ProviderException (retryable)     →  FallbackLLMAdapter tries next provider
└── AllProvidersFailedException       →  degraded-mode response to user
```

### 14.6 Full Conversation JSON Walkthrough

This walks through ONE complete booking from the user's first message to final confirmation, showing exactly what JSON flows where.

**Setup:** `sessionId = sess_abc`, `userId = u1`, system prompt from §11.1.

---

#### Turn 1 — User: *"I want to watch an action movie in Mumbai"*

**Step 1a — Session history BEFORE sending to Claude:**
```json
[
  { "role": "user", "content": "I want to watch an action movie in Mumbai" }
]
```
(System prompt is sent separately, not as a message.)

**Step 1b — Our POST to Claude** (`POST https://api.anthropic.com/v1/messages`):
```json
{
  "model": "claude-sonnet-4-6",
  "max_tokens": 2048,
  "system": "You are a friendly movie booking assistant...",
  "messages": [
    { "role": "user", "content": "I want to watch an action movie in Mumbai" }
  ],
  "tools": [
    { "name": "search_movies",        "description": "...", "input_schema": { ... } },
    { "name": "get_movie_details",    "description": "...", "input_schema": { ... } },
    { "name": "get_shows",            "description": "...", "input_schema": { ... } },
    { "name": "get_seat_availability","description": "...", "input_schema": { ... } },
    { "name": "preview_price",        "description": "...", "input_schema": { ... } },
    { "name": "apply_coupon",         "description": "...", "input_schema": { ... } },
    { "name": "create_booking",       "description": "...", "input_schema": { ... } },
    { "name": "process_payment",      "description": "...", "input_schema": { ... } },
    { "name": "get_user_bookings",    "description": "...", "input_schema": { ... } },
    { "name": "get_booking_details",  "description": "...", "input_schema": { ... } },
    { "name": "cancel_booking",       "description": "...", "input_schema": { ... } }
  ]
}
```

**Step 1c — Claude's response:**
```json
{
  "id": "msg_01abc",
  "role": "assistant",
  "stop_reason": "tool_use",
  "content": [
    { "type": "text", "text": "Sure! Let me find action movies in Mumbai for you." },
    {
      "type": "tool_use",
      "id": "toolu_01xyz",
      "name": "search_movies",
      "input": { "city": "Mumbai", "category": "Action" }
    }
  ]
}
```

**Step 1d — Our code:**
- Guard check: `search_movies` is Phase 1, allowed in any state. ✅
- Registry lookup: `SearchMoviesCommand` found.
- Execute: `MovieService.searchMovies("Mumbai", null, "Action", null)` returns 3 movies.

**Step 1e — Session history AFTER tool execution:**
```json
[
  { "role": "user", "content": "I want to watch an action movie in Mumbai" },
  { "role": "assistant", "content": [
      { "type": "text", "text": "Sure! Let me find action movies in Mumbai for you." },
      { "type": "tool_use", "id": "toolu_01xyz", "name": "search_movies",
        "input": { "city": "Mumbai", "category": "Action" } }
  ]},
  { "role": "user", "content": [
      { "type": "tool_result", "tool_use_id": "toolu_01xyz",
        "content": "[{\"movieId\":\"m1\",\"name\":\"Pushpa 2\",\"rating\":8.2},{\"movieId\":\"m2\",\"name\":\"Fighter\",\"rating\":7.8},{\"movieId\":\"m3\",\"name\":\"Animal\",\"rating\":7.1}]"
      }
  ]}
]
```

Loop continues.

---

#### Turn 1 — Loop iteration 2

**Step 2a — Our POST to Claude (SAME endpoint, updated history):**
```json
{
  "model": "claude-sonnet-4-6",
  "system": "...",
  "messages": [
    { "role": "user", "content": "I want to watch an action movie in Mumbai" },
    { "role": "assistant", "content": [
        { "type": "text", "text": "Sure! Let me find action movies..." },
        { "type": "tool_use", "id": "toolu_01xyz", "name": "search_movies",
          "input": { "city": "Mumbai", "category": "Action" } }
    ]},
    { "role": "user", "content": [
        { "type": "tool_result", "tool_use_id": "toolu_01xyz",
          "content": "[{movieId:m1, name:Pushpa 2, ...}, ...]" }
    ]}
  ],
  "tools": [ ... same 11 tools ... ]
}
```

> ⚠️ **Notice: we re-send the ENTIRE history every single call.** Claude has no memory between calls — we are its memory. This is why provider switching works.

**Step 2b — Claude's response:**
```json
{
  "stop_reason": "end_turn",
  "content": [
    { "type": "text", "text": "Here are 3 action movies playing in Mumbai:\n\n1. Pushpa 2 — ⭐ 8.2\n2. Fighter — ⭐ 7.8\n3. Animal — ⭐ 7.1\n\nWhich one catches your eye?" }
  ]
}
```

`stop_reason == "end_turn"` → append to history → return text to user. Turn 1 done.

---

#### Turn 2 — User: *"Pushpa 2, Saturday 2 PM IMAX"*

Pattern is identical. This turn's loop may trigger TWO tool calls (`get_shows` then `get_seat_availability`) before Claude returns text. Each iteration re-sends the full growing history.

---

#### Turn 3 — User: *"Book 2 Gold seats in the middle"*

Claude's loop: `preview_price` → `create_booking` → text reply.

**After `create_booking` returns `{ticketId: "tkt_123", amount: 1000, heldUntil: "...+5min", idempotencyKey: "idem_abc"}`, Claude's final text:**
```json
{
  "stop_reason": "end_turn",
  "content": [
    { "type": "text",
      "text": "I've held 2 Gold seats (G5, G6) for you.\n\nTotal: ₹1,000\nHold expires in 5 minutes.\n\nReady to pay? UPI / Credit Card / Netbanking?" }
  ]
}
```

Note: the seats are PENDING (not confirmed). Payment confirms.

---

#### Turn 4 — User: *"UPI, code FIRST50"*

Loop: `apply_coupon` → `process_payment` (with the same `idempotencyKey` from `create_booking`) → text confirmation.

By now the conversation history sent to Claude has ~15+ messages. Every single one is re-sent on every API call. That's the price of statelessness — but it's also the superpower: **any provider can pick up mid-conversation.**

### 14.7 Switching Providers Mid-Conversation — Worked Example

Imagine at **Turn 3** Claude times out while processing `create_booking`'s result.

1. Loop iteration: `FallbackLLMAdapter.sendWithTools(...)` → inside, `ClaudeAdapter` throws `TimeoutException` after 30s.
2. Fallback moves to next adapter: `OpenAIAdapter`.
3. `OpenAIAdapter.toWireFormat(history)` converts the canonical history to OpenAI's format:
   - User/assistant text messages → same shape
   - Claude's `tool_use` block → OpenAI's `tool_calls[]` inside the assistant message
   - Claude's `tool_result` block → OpenAI `role:"tool"` message with `tool_call_id`
4. `OpenAIAdapter` POSTs to `https://api.openai.com/v1/chat/completions` with the translated history + tool schemas (also translated once).
5. OpenAI sees the full picture: user asked for Pushpa 2 → search ran → shows returned → user picked 2 PM → seats shown → booking created. Now decide next.
6. OpenAI responds: *"Done! 2 Gold seats held. Total ₹1,000. How would you like to pay?"* — possibly worded differently from Claude's style, but **semantically equivalent**.
7. We append OpenAI's response to the canonical history as a normal `ASSISTANT` message with `producedByProvider = "openai"`.
8. Next turn may go back to Claude if it recovers. The user never notices the swap.

```
┌──────────────────────────────────────────────────────────────────────────┐
│  BEFORE the swap                         AFTER the swap                  │
│  ───────────────                          ──────────────                 │
│  [user]     "I want action movies"        [user]     (unchanged)         │
│  [claude]   tool_use: search_movies       [claude]   (unchanged)         │
│  [tool]     movies: [...]                 [tool]     (unchanged)         │
│  [claude]   "Pick one"                    [claude]   (unchanged)         │
│  [user]     "Pushpa 2, 2 PM IMAX"         [user]     (unchanged)         │
│  [claude]   tool_use: get_shows           [claude]   (unchanged)         │
│  [tool]     shows: [...]                  [tool]     (unchanged)         │
│  [claude]   tool_use: get_seats           [claude]   (unchanged)         │
│  [tool]     seats: {...}                  [tool]     (unchanged)         │
│  [claude]   "Seats held. Pay?"            [claude]   (unchanged)         │
│  [user]     "UPI, FIRST50"                [user]     (unchanged)         │
│                                                                          │
│  Loop iterates: Claude 💥 TIMEOUT   →  Loop retries: OpenAI ✅ ANSWERS   │
│                                                                          │
│                                          [openai]    "Payment done!"     │
│                                                                          │
│  History is identical. Only the LAST assistant message was produced      │
│  by a different provider. No data loss, no user disruption.              │
└──────────────────────────────────────────────────────────────────────────┘
```

### 14.8 Summary Cheat-Sheet (Print for Students)

```
╔══════════════════════════════════════════════════════════════════════╗
║  THE 5 RULES OF OUR AGENTIC SYSTEM                                   ║
║                                                                      ║
║  1. LLM is a REST service. stop_reason tells us what to do next.     ║
║  2. Tool descriptions ARE the LLM's docs. Write them carefully.      ║
║  3. Never trust LLM params. Validate every time.                     ║
║  4. Errors flow back as DATA (ToolResult.error), never exceptions.   ║
║  5. History lives with US. That's why provider swap works.           ║
║                                                                      ║
║  THE 3 LAYERS OF ERRORS                                              ║
║                                                                      ║
║  Tool-level   →  return error as tool_result, LLM self-corrects      ║
║  Loop-level   →  cap iterations, guardrails, summarise big results   ║
║  Provider-lvl →  fallback chain, canonical history, idempotency keys ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## 15. Testing Strategy

### 15.1 Unit Tests

| Component          | Test Cases                                                        |
|--------------------|-------------------------------------------------------------------|
| `ToolRegistry`     | All tools registered, unknown tool returns null, schema generation|
| `SearchMoviesCmd`  | Filters work, empty results, null params handled                 |
| `CreateBookingCmd` | Valid booking, seats taken, cutoff time                          |
| `ToolGuardService` | Premature payment blocked, search always allowed                 |
| `SessionManager`   | Create, get, expire, cleanup                                     |

### 15.2 Agent Integration Tests (Mock LLM)

```java
@SpringBootTest
class AgentServiceTest {

    @MockBean private LLMAdapter llmAdapter;
    @Autowired private AgentService agentService;

    @Test
    void fullBookingFlow() {
        // Mock LLM to return tool calls in sequence:
        // 1st call → tool_use: search_movies
        // 2nd call → text: "Here are the movies..."
        // 3rd call → tool_use: get_shows
        // ... etc

        String response = agentService.processMessage(sessionId, "Book a movie in Mumbai");
        assertThat(response).contains("Mumbai");
    }
}
```

### 15.3 WebSocket Tests

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AgentWebSocketTest {

    @Test
    void chatFlow() {
        WebSocketClient client = new StandardWebSocketClient();
        // Connect → send message → assert response received
    }
}
```

---

## 16. Implementation Timeline

### Day-by-Day Breakdown (April 21-27)

| Day | Date     | Focus                                   | Key Deliverable                          | Hours |
|-----|----------|-----------------------------------------|------------------------------------------|-------|
| 1   | Apr 21   | Base project (Models + Services)        | Core BookMyShow backend running          | ~5h   |
| 2   | Apr 22   | Base project (Controllers + DTOs)       | All REST APIs working in Postman         | ~4h   |
| 3   | Apr 23   | Agent foundation (ToolCommand, Registry, Session, LLMAdapter) | Agent loop compiles, mock tools work | ~4h |
| 4   | Apr 24   | All tool commands + ClaudeAgentAdapter  | Chat agent works end-to-end              | ~5h   |
| 5   | Apr 25   | WebSocket + Chat REST + Voice mode      | Both interaction modes working           | ~4h   |
| 6   | Apr 26   | Testing + error handling + polish       | All tests green, error handling complete | ~3h   |
| 7   | Apr 27   | Buffer / review                         | Project delivery-ready                   | ~2h   |
|     |          |                                         | **TOTAL**                                |**~27h**|

---

## 17. Instructor Delivery Plan — 3 Classes × 3 Hours

```
┌──────────────────────────────────────────────────────────────────────┐
│              BOOKMYSHOW — 3-CLASS DELIVERY MAP                       │
│                                                                      │
│  CLASS 1 (3 hrs)          CLASS 2 (3 hrs)         CLASS 3 (3 hrs)   │
│  ┌──────────────┐        ┌──────────────┐        ┌──────────────┐   │
│  │ BASE PROJECT │        │ AGENTIC AI   │        │ VOICE MODE   │   │
│  │ FROM SCRATCH │        │ CHAT AGENT   │        │ + TESTING    │   │
│  │              │        │              │        │ + POLISH     │   │
│  │ • Models     │        │ • Tool Call  │        │              │   │
│  │ • Services   │  ────▶ │   concept    │  ────▶ │ • STT + TTS │   │
│  │ • Controllers│        │ • Command    │        │ • WebSocket  │   │
│  │ • Pricing    │        │   Pattern    │        │ • Full test  │   │
│  │ • Postman    │        │ • Agent Loop │        │ • Live demo  │   │
│  └──────────────┘        └──────────────┘        └──────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

---

#### CLASS 1: Base BookMyShow Backend — From Scratch (3 hours)

```
CLASS 1 TIMELINE (180 min)
═══════════════════════════════════════════════════════════════════════

[0:00 - 0:15]  BLOCK 1: Project Setup (15 min)
───────────────────────────────────────────────
│ • Spring Boot project setup (spring-boot-starter-web, websocket, lombok)
│ • Package structure: model/ service/ controller/ dto/ config/
│ • Explain the domain: Cities → Theaters → Halls → Seats → Shows


[0:15 - 0:55]  BLOCK 2: Models + Enums (40 min)
─────────────────────────────────────────────────
│ • City, Theater, Hall models
│ • Seat + SeatType enum (GOLD, DIAMOND, PLATINUM)
│ • Show + ShowSeat + SeatStatus enum (AVAILABLE, BOOKED)
│ • Movie model (name, rating, category, languages)
│ • Ticket + TicketStatus enum (BOOKED, CANCELLED)
│ • Payment + PaymentMode enum + PaymentStatus enum
│ • InMemoryStore (maps for all entities)
│
│ 💡 "This is a rich domain. Notice how ShowSeat bridges Show and Seat —
│     it tracks availability PER SHOW, not per seat globally."
│
│ Files: ~12 model classes + 5 enums + InMemoryStore


[0:55 - 1:05]  ☕ BREAK (10 min)


[1:05 - 1:50]  BLOCK 3: Services — Business Logic (45 min)
───────────────────────────────────────────────────────────
│ • MovieService — search with filters (city, language, category, rating)
│ • ShowService — getShows, getShowSeats
│ • BookingService — createBooking (check availability, lock seats, create ticket)
│   💡 "Race condition! Two users book same seat simultaneously.
│       Use synchronized/locks on ShowSeat status updates."
│ • PaymentService — processPayment (simulate UPI/CC/NB)
│ • PricingService — calculate price based on:
│   seat type + day of week + time of day + movie + hall
│   💡 "This is real-world pricing logic. MoviePass vs weekend premium."
│ • CouponService — validate and apply promo codes
│ • AdminService — addMovie, addShow, addTheater (seed data)
│
│ Files: 7 service classes


[1:50 - 2:25]  BLOCK 4: REST Controllers + DTOs (35 min)
────────────────────────────────────────────────────────
│ • MovieController: GET /movies?city&lang&category
│ • ShowController: GET /movies/{id}/shows, GET /shows/{id}/seats
│ • BookingController: POST /bookings, DELETE /bookings/{id}
│ • PaymentController: POST /payments
│ • AdminController: POST /admin/movies, POST /admin/shows (seed data)
│ • Request/Response DTOs
│ • GlobalExceptionHandler
│
│ Files: 5 controllers + ~8 DTOs + exception handler


[2:25 - 2:50]  BLOCK 5: Seed Data + Postman Demo (25 min)
────────────────────────────────────────────────────────
│ • Create DataSeeder @Component — loads sample data on startup:
│   - 2 cities (Mumbai, Delhi)
│   - 3 theaters per city, 2 halls each
│   - 5 movies with shows across theaters
│   - Seats with pricing
│
│ • LIVE POSTMAN DEMO:
│   1. GET /movies?city=Mumbai → list of movies
│   2. GET /movies/{id}/shows → showtimes
│   3. GET /shows/{id}/seats → seat map with availability
│   4. POST /bookings → book 2 Gold seats
│   5. POST /payments → pay via UPI
│   6. GET /shows/{id}/seats again → those seats now BOOKED
│   7. DELETE /bookings/{id} → cancel
│
│ Files: DataSeeder.java


[2:50 - 3:00]  BLOCK 6: Recap + Preview (10 min)
─────────────────────────────────────────────────
│ • "We have a complete booking backend. 5+ REST APIs. Postman tested."
│ • Preview: "Next class, we build an AI agent that does ALL of this
│   through conversation. The agent will call the same services we built today."
│ • Homework: Read about Claude Tool Calling / Function Calling

═══════════════════════════════════════════════════════════════════════

CLASS 1 FILES: ~35 files (models + services + controllers + DTOs + seeder)
```

---

#### CLASS 2: Agentic AI — Chat Booking Agent (3 hours)

```
CLASS 2 TIMELINE (180 min)
═══════════════════════════════════════════════════════════════════════

[0:00 - 0:20]  BLOCK 1: What is Agentic AI? + Tool Calling Theory (20 min)
──────────────────────────────────────────────────────────────────────────
│ • "Last class: user calls 5 APIs in sequence. Today: AI agent does it."
│
│ • Explain the paradigm shift (on whiteboard):
│   Traditional: User → API → Response → User → API → Response
│   Agentic:     User → Agent → [tool → tool → tool] → Response
│
│ • Show Claude tool calling docs:
│   "You give the LLM a list of tools it CAN call.
│    The LLM DECIDES which tool to call based on the conversation.
│    You execute the tool and feed the result back.
│    The LLM continues until it has a final answer."
│
│ • Draw THE LOOP on whiteboard:
│   User msg → LLM → tool_use? → YES → execute → feed result → LLM → ...
│                                  NO → return text to user
│
│ 💡 "This is the most important AI pattern in 2026. ChatGPT plugins,
│     Claude computer use, Cursor AI — they all use this loop."


[0:20 - 0:45]  BLOCK 2: Command Pattern + ToolCommand Interface (25 min)
────────────────────────────────────────────────────────────────────────
│ • Explain Command pattern:
│   "9 tools. We need a clean way to register and execute them.
│    Each tool is a Command object with a name, schema, and execute()."
│
│ • Build ToolCommand interface
│ • Build ToolResult class (success/error wrapper)
│ • Build ToolRegistry @Component (auto-collects all commands)
│
│ • Build first 3 commands (students follow along):
│   - SearchMoviesCommand (calls MovieService.searchMovies)
│   - GetShowsCommand (calls ShowService.getShows)
│   - GetSeatAvailabilityCommand (calls ShowService.getShowSeats)
│
│ 💡 "Notice each Command just wraps an existing service method.
│     The agent doesn't call services directly — it goes through commands."
│
│ Files: ToolCommand.java, ToolResult.java, ToolRegistry.java,
│        SearchMoviesCommand, GetShowsCommand, GetSeatAvailabilityCommand


[0:45 - 1:00]  BLOCK 3: Remaining Tool Commands (15 min)
────────────────────────────────────────────────────────
│ • Build remaining commands (faster — students see the pattern):
│   - CreateBookingCommand (with confirmation guardrail)
│   - ApplyCouponCommand
│   - ProcessPaymentCommand
│   - CancelBookingCommand
│   - GetBookingDetailsCommand
│   - GetUserBookingsCommand
│
│ Files: 6 more command classes


[1:00 - 1:10]  ☕ BREAK (10 min)


[1:10 - 1:40]  BLOCK 4: LLMAdapter + Claude Integration (30 min)
───────────────────────────────────────────────────────────────
│ • Define LLMAdapter interface:
│   LLMResponse sendWithTools(systemPrompt, history, toolSchemas)
│
│ • Build LLMResponse class:
│   - isTextResponse() / isToolUse()
│   - getText() / getToolCall()
│
│ • Implement ClaudeAgentAdapter:
│   - Build API request with tools array
│   - Send conversation history (multi-turn)
│   - Parse response: check stop_reason
│     "end_turn" → text, "tool_use" → extract tool name + params
│
│ • Build the system prompt:
│   Personality, rules, context injection
│   💡 "The system prompt IS the agent's personality. Spend time on this."
│
│ 🔴 LIVE DEMO: Hardcode one user message → show Claude returning tool_use
│   "Watch — Claude didn't answer in text. It returned:
│    tool_use: search_movies, params: {city: 'Mumbai'}
│    It DECIDED to search the database!"
│
│ Files: LLMAdapter.java, LLMResponse.java, ToolCall.java,
│        ClaudeAgentAdapter.java


[1:40 - 2:10]  BLOCK 5: AgentService — THE AGENTIC LOOP (30 min)
────────────────────────────────────────────────────────────────
│ • Build SessionManager (stores conversation state + history)
│ • Build ConversationState enum
│ • Build ToolGuardService (state-based guardrails)
│
│ • Build AgentService — THE MOST IMPORTANT CLASS:
│   processMessage(sessionId, userMessage):
│     1. Get session, append user message to history
│     2. WHILE LOOP:
│        a. Call LLM with history + tools
│        b. If text response → return to user (break)
│        c. If tool_use:
│           - Validate with ToolGuardService
│           - Look up command in ToolRegistry
│           - Execute command
│           - Append tool_use + tool_result to history
│           - Continue loop (LLM processes result)
│
│ 💡 TEACHING MOMENT (on whiteboard):
│   "The loop is elegant. Claude calls search_movies → gets results →
│    decides to present them to user → returns text. 
│    OR if user already picked a movie, Claude calls get_shows next.
│    THE LLM DECIDES. We just execute and loop."
│
│ 🔴 LIVE CODE THE WHILE LOOP — this is the climax of the class
│
│ Files: SessionManager.java, ConversationState.java,
│        ToolGuardService.java, AgentService.java


[2:10 - 2:40]  BLOCK 6: Chat REST Endpoint + Live Demo (30 min)
──────────────────────────────────────────────────────────────
│ • Build AgentChatController @RestController:
│   POST /api/agent/chat/start → returns sessionId + greeting
│   POST /api/agent/chat/send  → returns agent response
│   POST /api/agent/chat/end   → cleans up session
│
│ 🔴 THE BIG DEMO — Full booking via Postman:
│
│   1. POST /chat/start
│      → "Hi! I'm your BookMyShow assistant..."
│
│   2. POST /chat/send { message: "Show me action movies in Mumbai" }
│      → Agent calls search_movies → returns movie list
│
│   3. POST /chat/send { message: "Pushpa 2, Saturday 2pm IMAX" }
│      → Agent calls get_shows → get_seats → presents options
│
│   4. POST /chat/send { message: "2 Gold seats in the middle" }
│      → Agent calls create_booking → shows summary → asks to confirm
│
│   5. POST /chat/send { message: "Yes, pay via UPI, code FIRST50" }
│      → Agent calls apply_coupon → process_payment → CONFIRMED!
│
│ 💡 "We just booked a movie through a CONVERSATION. 5 tool calls,
│     all decided by the AI, all in one smooth flow."
│
│ Students try it themselves (5 min)
│
│ Files: AgentChatController.java


[2:40 - 3:00]  BLOCK 7: Recap + Preview (20 min)
────────────────────────────────────────────────
│ • Recap: "We built an autonomous agent. It searches, browses,
│   books, and pays — all by deciding which tool to call."
│ • Show the conversation history that was sent to Claude
│   (user, assistant, tool_use, tool_result messages)
│ • Preview Class 3: "Next: WebSocket for real-time chat,
│   voice mode with STT + TTS, and testing."
│ • Homework: Try making the agent handle edge cases
│   ("What if I ask for a movie that doesn't exist?")

═══════════════════════════════════════════════════════════════════════

CLASS 2 FILES: ~20 files (commands + adapter + agent service + controller)
```

---

#### CLASS 3: Voice Mode + WebSocket + Testing + Polish (3 hours)

```
CLASS 3 TIMELINE (180 min)
═══════════════════════════════════════════════════════════════════════

[0:00 - 0:30]  BLOCK 1: WebSocket Chat Integration (30 min)
──────────────────────────────────────────────────────────
│ • Add spring-boot-starter-websocket dependency
│ • Build WebSocketConfig @Configuration
│ • Build AgentWebSocketHandler:
│   - onOpen: create session
│   - onMessage: call agentService.processMessage()
│   - Send response back through WebSocket
│   - onClose: cleanup session
│
│ • Demo with WebSocket client (browser/Postman):
│   Real-time back-and-forth conversation!
│
│ 💡 "REST was request-response. WebSocket is persistent.
│     The agent can now push messages, stream typing indicators."
│
│ Files: WebSocketConfig.java, AgentWebSocketHandler.java


[0:30 - 1:00]  BLOCK 2: Voice Mode — STT + TTS (30 min)
────────────────────────────────────────────────────────
│ • Reuse STTAdapter from Splitwise (WhisperSTTAdapter)
│   💡 "This is why Adapter pattern across projects is powerful.
│       Same interface, same code."
│
│ • Build TTSAdapter interface:
│   byte[] synthesize(String text, String voice)
│
│ • Implement OpenAITTSAdapter:
│   POST to OpenAI TTS API → get audio bytes → save as MP3
│
│ • Build VoiceAgentController @RestController:
│   POST /voice/start → create session
│   POST /voice/turn  → STT(audio) → agentService → TTS(response) → return
│   POST /voice/end   → cleanup
│   GET  /voice/audio/{id} → serve audio file
│
│ • Show the architecture:
│   "Voice mode = STT wrapper + SAME agent + TTS wrapper.
│    The agent doesn't know if the user is typing or speaking."
│
│ Files: TTSAdapter.java, OpenAITTSAdapter.java, VoiceAgentController.java


[1:00 - 1:10]  ☕ BREAK (10 min)


[1:10 - 1:30]  BLOCK 3: Voice Demo + Error Handling (20 min)
───────────────────────────────────────────────────────────
│ • LIVE DEMO: Send audio via Postman → get text + audio response
│
│ • Build AgentExceptionHandler @RestControllerAdvice:
│   - SessionNotFound → 404
│   - PrematureToolCall → 400 (agent retries with different approach)
│   - ToolExecution → agent says "Sorry, something went wrong"
│   - AIProvider → 503 + fallback message
│   - PaymentFailed → agent offers retry
│
│ • Show how the agent handles errors GRACEFULLY:
│   Agent: "I tried to book those seats but they were just taken.
│           Let me check what else is available..."
│   (Agent calls get_seat_availability again automatically)
│
│ Files: AgentExceptionHandler.java + exception classes


[1:30 - 2:10]  BLOCK 4: Testing (40 min)
─────────────────────────────────────────
│ • Build MockLLMAdapter (returns scripted tool calls):
│   "This is how you test an agent without paying for API calls."
│
│ • Unit tests — ToolRegistry:
│   @Test allToolsRegistered()
│   @Test unknownTool_returnsNull()
│
│ • Unit tests — ToolGuardService:
│   @Test paymentBlocked_beforeBooking()
│   @Test searchAllowed_inAnyState()
│
│ • Unit tests — individual commands:
│   @Test searchMovies_filtersCorrectly()
│   @Test createBooking_seatsTaken_throwsException()
│
│ • Integration test — AgentService:
│   Mock LLM to return sequence of tool calls
│   Verify: correct tools called in correct order
│   Verify: final response contains booking confirmation
│
│ • WebSocket test:
│   Connect → send message → assert response received
│
│ • Run: mvn test → ALL GREEN ✅
│
│ Files: MockLLMAdapter, ToolRegistryTest, ToolGuardServiceTest,
│        SearchMoviesCommandTest, AgentServiceIntegrationTest,
│        WebSocketIntegrationTest


[2:10 - 2:30]  BLOCK 5: Advanced — Multi-Tool Calls + Edge Cases (20 min)
─────────────────────────────────────────────────────────────────────────
│ • Show Claude calling MULTIPLE tools in one turn:
│   User: "Book Pushpa 2, tomorrow 7pm IMAX, 2 Gold seats, pay UPI"
│   Agent: search → get_shows → get_seats → create_booking → process_payment
│   (5 tool calls in one agentic loop!)
│
│ • Edge cases:
│   - "Cancel my last booking" → agent calls get_user_bookings first
│   - "What's the rating of Fighter?" → agent uses get_movie_details
│   - "I changed my mind" mid-booking → agent handles gracefully
│   - Movie doesn't exist → agent suggests alternatives
│
│ • Show how system prompt tweaks change behavior:
│   Add "Always suggest snacks combo" → agent upsells
│   Add "Be extremely concise" → shorter responses


[2:30 - 2:50]  BLOCK 6: Provider Swap + Architecture Review (20 min)
────────────────────────────────────────────────────────────────────
│ • Swap ai.llm.provider: claude → openai (if OpenAI adapter built)
│ • Swap ai.tts.provider: openai → elevenlabs
│ • Show application.yml — all AI config in one place
│
│ • Final architecture diagram walkthrough:
│   "Two input channels (chat + voice) → one agent brain →
│    tool commands → existing services. Clean separation."
│
│ • Compare with Splitwise:
│   Splitwise: AI as input enhancer (one-shot)
│   BookMyShow: AI as autonomous agent (multi-turn)
│   "Same patterns (Adapter), different AI paradigm."


[2:50 - 3:00]  BLOCK 7: Final Recap (10 min)
─────────────────────────────────────────────
│ • 3-class journey:
│   Class 1: "Solid backend with booking flow"
│   Class 2: "AI agent that autonomously books movies via chat"
│   Class 3: "Voice mode + WebSocket + full test suite"
│
│ • Key concepts taught:
│   ✅ Agentic AI / Tool Calling (THE pattern of 2026)
│   ✅ Command Pattern (tool execution)
│   ✅ State Pattern (conversation guardrails)
│   ✅ Adapter Pattern (provider abstraction)
│   ✅ WebSocket (persistent bidirectional comms)
│   ✅ STT + TTS integration
│   ✅ System prompt engineering for agents
│   ✅ Testing autonomous agents
│
│ END OF CLASS 3 ✅ — PROJECT COMPLETE 🚀

═══════════════════════════════════════════════════════════════════════

CLASS 3 FILES: ~15 files (WebSocket + voice + tests)
GRAND TOTAL: ~70 files across 3 classes
```

---

## 18. Appendix

### 18.1 Project Structure (New Files Only — AI Extension)

```
src/main/java/com/bookmyshow/
├── model/                          (existing — Class 1)
├── service/                        (existing — Class 1)
├── controller/                     (existing — Class 1)
├── dto/                            (existing — Class 1)
│
├── agent/                          ★ NEW — all agentic AI code
│   ├── core/
│   │   ├── AgentService.java                ← THE agentic loop
│   │   ├── SessionManager.java              ← conversation state + history
│   │   ├── ConversationState.java           ← enum
│   │   └── ToolGuardService.java            ← state-based guardrails
│   ├── tool/
│   │   ├── ToolCommand.java                 ← interface (Command Pattern)
│   │   ├── ToolResult.java                  ← success/error wrapper — errors flow as DATA
│   │   ├── ToolRegistry.java                ← auto-collects all commands
│   │   ├── SearchMoviesCommand.java         ← Phase 1 — discovery
│   │   ├── GetMovieDetailsCommand.java      ← Phase 1
│   │   ├── GetShowsCommand.java             ← Phase 1
│   │   ├── GetSeatAvailabilityCommand.java  ← Phase 1
│   │   ├── PreviewPriceCommand.java         ← Phase 2 — preview (NEW)
│   │   ├── ApplyCouponCommand.java          ← Phase 2
│   │   ├── CreateBookingCommand.java        ← Phase 3 — write (PENDING + 5-min hold)
│   │   ├── ProcessPaymentCommand.java       ← Phase 3 (idempotency-key protected)
│   │   ├── GetUserBookingsCommand.java      ← Phase 4
│   │   ├── GetBookingDetailsCommand.java    ← Phase 4
│   │   └── CancelBookingCommand.java        ← Phase 4
│   ├── message/
│   │   ├── Message.java                     ← canonical message (provider-agnostic)
│   │   ├── Role.java                        ← USER / ASSISTANT / TOOL_RESULT
│   │   ├── ToolUseBlock.java
│   │   └── ToolResultBlock.java
│   ├── adapter/
│   │   ├── LLMAdapter.java                  ← interface
│   │   ├── LLMResponse.java
│   │   ├── ToolCall.java
│   │   ├── FallbackLLMAdapter.java          ← @Primary — wraps the chain (NEW)
│   │   ├── ClaudeAgentAdapter.java
│   │   ├── OpenAIAgentAdapter.java          ← fallback #1 (NEW)
│   │   ├── GeminiAgentAdapter.java          ← fallback #2 (NEW, optional)
│   │   ├── STTAdapter.java                  ← interface (reuse from P1)
│   │   ├── WhisperSTTAdapter.java
│   │   ├── TTSAdapter.java                  ← interface (NEW)
│   │   └── OpenAITTSAdapter.java
│   ├── chat/
│   │   ├── AgentChatController.java         ← REST fallback
│   │   └── AgentWebSocketHandler.java       ← WebSocket
│   ├── voice/
│   │   └── VoiceAgentController.java
│   ├── exception/
│   │   ├── AgentException.java
│   │   ├── SessionNotFoundException.java
│   │   ├── SessionExpiredException.java
│   │   ├── PrematureToolCallException.java
│   │   ├── UnknownToolException.java
│   │   ├── ToolExecutionException.java
│   │   ├── SeatAlreadyBookedException.java
│   │   ├── CutoffExceededException.java
│   │   ├── PaymentFailedException.java
│   │   ├── ProviderException.java           ← retryable — triggers fallback (NEW)
│   │   ├── AllProvidersFailedException.java ← terminal — degraded mode (NEW)
│   │   └── AgentExceptionHandler.java
│   └── config/
│       ├── AgentConfig.java
│       └── WebSocketConfig.java

src/test/java/com/bookmyshow/agent/
├── mock/MockLLMAdapter.java
├── core/AgentServiceTest.java
├── tool/ToolRegistryTest.java
├── tool/ToolGuardServiceTest.java
├── tool/SearchMoviesCommandTest.java
├── chat/WebSocketIntegrationTest.java
└── AgentIntegrationTest.java
```

### 18.2 Maven Dependencies to Add

```xml
<!-- WebSocket support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>

<!-- HTTP client for AI API calls -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- JSON processing -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### 18.3 application.yml — AI Configuration

```yaml
ai:
  llm:
    # Provider chain: primary is tried first, fallbacks run in order on failure
    primary: claude                     # "claude" | "openai" | "gemini" | "mock"
    fallbacks: [openai, gemini]         # empty list = no fallback (primary-only)
    retry-per-provider: 2               # attempts per provider before moving on
    per-call-timeout-seconds: 30
    total-turn-timeout-seconds: 60
  stt:
    provider: whisper                   # "whisper" | "deepgram" | "mock"
  tts:
    provider: openai                    # "openai" | "elevenlabs" | "mock"

  claude:
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-sonnet-4-6
    max-tokens: 2048

  openai:
    api-key: ${OPENAI_API_KEY:}
    chat-model: gpt-4o
    whisper-model: whisper-1
    tts-model: tts-1
    tts-voice: alloy

  gemini:
    api-key: ${GEMINI_API_KEY:}
    model: gemini-1.5-pro

  elevenlabs:
    api-key: ${ELEVENLABS_API_KEY:}
    voice-id: default

agent:
  session-timeout-minutes: 30
  max-tool-calls-per-turn: 10
  pending-booking-hold-minutes: 5       # how long PENDING tickets lock seats
  system-prompt-path: classpath:prompts/booking-agent.txt

spring:
  servlet:
    multipart:
      max-file-size: 25MB
```

### 18.5 Full Tool Schemas Sent to Claude

This is the complete `tools` array that accompanies every `/v1/messages` call. Generated from the `ToolCommand` implementations at startup.

```json
[
  {
    "name": "search_movies",
    "description": "Find currently playing movies in a city. Use when the user is browsing, wants recommendations, or hasn't picked a specific movie yet. If the user hasn't named a city, ASK them — do NOT guess a city.",
    "input_schema": {
      "type": "object",
      "properties": {
        "city":       { "type": "string" },
        "language":   { "type": "string" },
        "category":   { "type": "string" },
        "min_rating": { "type": "number" }
      },
      "required": ["city"]
    }
  },
  {
    "name": "get_movie_details",
    "description": "Get full details (rating, synopsis, cast, duration) for a single movie. Use when the user asks about a specific movie.",
    "input_schema": {
      "type": "object",
      "properties": { "movie_id": { "type": "string" } },
      "required": ["movie_id"]
    }
  },
  {
    "name": "get_shows",
    "description": "Get showtimes for a specific movie in a city, optionally on a specific date. Use AFTER the user has picked a movie.",
    "input_schema": {
      "type": "object",
      "properties": {
        "movie_id": { "type": "string" },
        "city":     { "type": "string" },
        "date":     { "type": "string", "description": "YYYY-MM-DD" }
      },
      "required": ["movie_id", "city"]
    }
  },
  {
    "name": "get_seat_availability",
    "description": "Get available seats for a show (grouped by seat type with prices). Use AFTER the user has picked a showtime.",
    "input_schema": {
      "type": "object",
      "properties": { "show_id": { "type": "string" } },
      "required": ["show_id"]
    }
  },
  {
    "name": "preview_price",
    "description": "Calculate the total price (with optional coupon) BEFORE booking. Use this to tell the user what it will cost before any commitment. Does NOT lock any seats.",
    "input_schema": {
      "type": "object",
      "properties": {
        "show_id":      { "type": "string" },
        "seat_ids":     { "type": "array", "items": { "type": "string" } },
        "coupon_code":  { "type": "string" }
      },
      "required": ["show_id", "seat_ids"]
    }
  },
  {
    "name": "apply_coupon",
    "description": "Validate a coupon/promo code. Does NOT apply it to a booking yet — only checks if it's valid and what the discount would be.",
    "input_schema": {
      "type": "object",
      "properties": {
        "coupon_code": { "type": "string" },
        "amount":      { "type": "number" },
        "user_id":     { "type": "string" }
      },
      "required": ["coupon_code", "amount", "user_id"]
    }
  },
  {
    "name": "create_booking",
    "description": "Lock seats and create a PENDING ticket (5-minute hold). ALWAYS confirm with the user first — read back the movie, showtime, seats, and total before calling. Seats are released if payment isn't completed within the hold window.",
    "input_schema": {
      "type": "object",
      "properties": {
        "user_id":  { "type": "string" },
        "show_id":  { "type": "string" },
        "seat_ids": { "type": "array", "items": { "type": "string" } }
      },
      "required": ["user_id", "show_id", "seat_ids"]
    }
  },
  {
    "name": "process_payment",
    "description": "Charge for a pending booking. ALWAYS confirm amount and payment mode with the user first. The idempotency_key MUST be the one returned by create_booking — do not generate your own.",
    "input_schema": {
      "type": "object",
      "properties": {
        "ticket_id":       { "type": "string" },
        "payment_mode":    { "type": "string", "enum": ["UPI", "CREDIT_CARD", "NETBANKING"] },
        "amount":          { "type": "number" },
        "idempotency_key": { "type": "string" }
      },
      "required": ["ticket_id", "payment_mode", "amount", "idempotency_key"]
    }
  },
  {
    "name": "get_user_bookings",
    "description": "List the user's bookings. Use when the user says things like 'my bookings', 'what did I book', or before 'cancel my last booking'.",
    "input_schema": {
      "type": "object",
      "properties": {
        "user_id": { "type": "string" },
        "status":  { "type": "string", "enum": ["BOOKED", "CANCELLED", "PENDING", "ALL"] }
      },
      "required": ["user_id"]
    }
  },
  {
    "name": "get_booking_details",
    "description": "Get full details of one booking by ticket ID.",
    "input_schema": {
      "type": "object",
      "properties": { "ticket_id": { "type": "string" } },
      "required": ["ticket_id"]
    }
  },
  {
    "name": "cancel_booking",
    "description": "Cancel an existing booking. Refuses if within 1 hour of showtime. ALWAYS confirm with the user before calling.",
    "input_schema": {
      "type": "object",
      "properties": {
        "ticket_id": { "type": "string" },
        "user_id":   { "type": "string" }
      },
      "required": ["ticket_id", "user_id"]
    }
  }
]
```

### 18.4 Comparison: Splitwise vs BookMyShow AI

| Dimension           | Splitwise (Project 1)           | BookMyShow (Project 2)              |
|---------------------|---------------------------------|-------------------------------------|
| AI Role             | Input enhancer (one-shot)       | Autonomous agent (multi-turn)       |
| Interaction         | Single API call                 | Ongoing conversation                |
| LLM Usage           | Parse/extract structured data   | Decide actions + call tools + chat  |
| Tool Calling        | Not used                        | Core architecture                   |
| Communication       | REST only                       | REST + WebSocket                    |
| Design Patterns     | Adapter + Strategy              | Adapter + Command + State           |
| Voice               | STT only (input)                | STT + TTS (full duplex)             |
| Complexity          | Medium                          | High                                |
| Files               | ~45                             | ~70                                 |
| Teaching Hours      | 9 hrs (3×3)                     | 9 hrs (3×3)                         |
