package com.example.bookmyshowoct24.chat;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/*
 * Guardrails for the agent loop. Validates that the tool the LLM is trying to call
 * is allowed given the current conversation state.
 *
 * Why this exists: the system prompt tells the LLM "confirm before paying", but a
 * hallucinated or prompt-injected LLM could still call process_payment straight away.
 * This service is the code-level enforcement — even if the LLM misbehaves, it can't
 * bypass these rules.
 *
 * Rules (for each tool -> set of states in which it's allowed):
 *   create_booking   -> IDLE or BOOKING_CONFIRMED (starting fresh after a prior booking)
 *   process_payment  -> BOOKING_PENDING only (must create a booking first)
 *   cancel_booking   -> BOOKING_PENDING or BOOKING_CONFIRMED (can't cancel nothing)
 *   everything else  -> any state (read-only tools)
 */
@Service
public class ToolGuardService {

    private static final Map<String, Set<ConversationState>> ALLOWED_STATES = Map.of(
            "create_booking", Set.of(ConversationState.IDLE, ConversationState.BOOKING_CONFIRMED),
            "process_payment", Set.of(ConversationState.BOOKING_PENDING),
            "cancel_booking", Set.of(ConversationState.BOOKING_PENDING, ConversationState.BOOKING_CONFIRMED)
    );

    public GuardResult check(String toolName, ConversationState state) {
        Set<ConversationState> allowed = ALLOWED_STATES.get(toolName);
        if (allowed == null || allowed.contains(state)) {
            return GuardResult.allow();
        }
        String reason = String.format(
                "Tool '%s' cannot be called in state %s. Allowed states: %s. "
                        + "Hint: the conversation flow is IDLE -> create_booking -> BOOKING_PENDING "
                        + "-> process_payment -> BOOKING_CONFIRMED.",
                toolName, state, allowed);
        return GuardResult.deny(reason);
    }

    // After a tool runs successfully, advance the state machine.
    // Failed tool calls don't change state (caller should check isError first).
    public ConversationState nextState(String toolName, ConversationState current) {
        return switch (toolName) {
            case "create_booking" -> ConversationState.BOOKING_PENDING;
            case "process_payment" -> ConversationState.BOOKING_CONFIRMED;
            case "cancel_booking" -> ConversationState.IDLE;
            default -> current;
        };
    }

    public record GuardResult(boolean allowed, String reason) {
        public static GuardResult allow() {
            return new GuardResult(true, null);
        }

        public static GuardResult deny(String reason) {
            return new GuardResult(false, reason);
        }
    }
}
