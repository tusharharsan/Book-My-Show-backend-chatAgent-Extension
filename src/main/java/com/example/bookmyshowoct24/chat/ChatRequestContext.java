package com.example.bookmyshowoct24.chat;

/*
 * Per-request context for tool execution. Holds two pieces of request-scoped
 * data that @Tool methods need but that we deliberately do NOT expose to the
 * LLM through the tool schema:
 *
 *   1. userId    — the authenticated user. If we accepted it as a tool arg, a
 *                  prompt-injected LLM could book tickets on someone else's
 *                  account. So tools read it from here, not from their input.
 *
 *   2. sessionId — the chat session the tool is running inside. Mutating tools
 *                  (create_booking / process_payment / cancel_booking) read
 *                  and write conversation state (IDLE / BOOKING_PENDING /
 *                  BOOKING_CONFIRMED) keyed by this sessionId via SessionManager.
 *
 * The orchestrator (BookMyShowAgentService) populates both fields *before*
 * invoking ChatClient.call() and clears them in a finally block. This matters
 * because Spring reuses request threads — forgetting to clear the ThreadLocal
 * would silently leak one request's user/session into the next.
 */
public final class ChatRequestContext {
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SESSION = new ThreadLocal<>();

    private ChatRequestContext() {}

    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static void setSessionId(String sessionId) {
        CURRENT_SESSION.set(sessionId);
    }

    public static String getSessionId() {
        return CURRENT_SESSION.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_SESSION.remove();
    }
}
