package com.example.bookmyshowoct24.chat;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * In-memory per-session state store.
 *
 * Scope (post-Spring-AI refactor): this class now ONLY tracks
 * {@link ConversationState} (IDLE / BOOKING_PENDING / BOOKING_CONFIRMED) keyed
 * by sessionId. The @Tool methods in ai.tools.* read and advance that state
 * via {@link ToolGuardService}.
 *
 * Conversation HISTORY (i.e. the chat transcript that used to live in this
 * class as a List<MessageParam>) has moved to Spring AI's own abstraction —
 * see {@code MessageWindowChatMemory} + {@code InMemoryChatMemoryRepository}
 * wired inside {@code BookMyShowAgentService}. That layer is provider-agnostic,
 * so Gemini, Anthropic, OpenAI, and Ollama all see the same history.
 *
 * For a real system this state would live in Redis or a DB; in-memory is
 * fine for a teaching project where restarts are acceptable.
 */
@Component
public class SessionManager {
    private final Map<String, ConversationState> states = new ConcurrentHashMap<>();

    public ConversationState getState(String sessionId) {
        return states.getOrDefault(sessionId, ConversationState.IDLE);
    }

    public void setState(String sessionId, ConversationState state) {
        states.put(sessionId, state);
    }

    public void endSession(String sessionId) {
        states.remove(sessionId);
    }
}
