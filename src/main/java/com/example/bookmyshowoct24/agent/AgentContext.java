package com.example.bookmyshowoct24.agent;

/*
 * Per-request context for tool execution. Holds the authenticated userId so tools
 * don't have to take it from the LLM's input (which would let a prompt-injected
 * user book tickets on someone else's account).
 *
 * AgentService sets this before the tool loop and clears it in a finally block.
 * Tools read via AgentContext.getUserId().
 */
public final class AgentContext {
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private AgentContext() {}

    public static void setUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long getUserId() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
