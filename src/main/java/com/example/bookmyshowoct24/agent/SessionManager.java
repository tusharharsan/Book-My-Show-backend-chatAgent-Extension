package com.example.bookmyshowoct24.agent;

import com.anthropic.models.messages.MessageParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * In-memory per-session data: conversation history + current ConversationState.
 *
 * A "session" = one user's chat/voice call. History grows across turns so Claude
 * remembers context; state tracks where the user is in the booking flow so
 * ToolGuardService can block tools that don't make sense yet.
 *
 * For a real system this would be Redis or a DB. In-memory is fine for a student
 * project — it's wiped on restart.
 */
@Component
public class SessionManager {
    private final Map<String, List<MessageParam>> histories = new ConcurrentHashMap<>();
    private final Map<String, ConversationState> states = new ConcurrentHashMap<>();

    public List<MessageParam> getHistory(String sessionId) {
        // Lambda is required here: ConcurrentHashMap.computeIfAbsent is atomic, so
        // two threads starting the same sessionId at once will only create one list.
        // The "get-if-null-then-put" pattern would race and occasionally create two.
        return histories.computeIfAbsent(sessionId, id -> new ArrayList<>());
    }

    public void append(String sessionId, MessageParam message) {
        getHistory(sessionId).add(message);
    }

    public ConversationState getState(String sessionId) {
        return states.getOrDefault(sessionId, ConversationState.IDLE);
    }

    public void setState(String sessionId, ConversationState state) {
        states.put(sessionId, state);
    }

    public void endSession(String sessionId) {
        histories.remove(sessionId);
        states.remove(sessionId);
    }
}
