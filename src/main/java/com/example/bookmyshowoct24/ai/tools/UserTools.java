package com.example.bookmyshowoct24.ai.tools;

import com.example.bookmyshowoct24.chat.ChatRequestContext;
import com.example.bookmyshowoct24.models.User;
import com.example.bookmyshowoct24.services.UserService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tools facade for User-related lookups.
 *
 * <p>The one tool here — {@code get_current_user} — deliberately has
 * <strong>zero parameters</strong>. The user id it looks up comes from
 * {@link ChatRequestContext}, populated by the orchestrator from the authenticated
 * HTTP session. This is the exact same prompt-injection defence used by
 * Ticketing tools: never give the LLM the chance to ask about anyone's
 * profile but its own caller's.
 *
 * <p>Return shape: we do NOT return the raw {@link User} JPA entity because
 * that would include the BCrypt password hash and the full {@code bookings}
 * relationship. Instead we map to a purpose-built {@link UserProfile} record
 * that exposes only the fields the LLM should see.
 */
@Component
public class UserTools {

    private final UserService userService;

    public UserTools(UserService userService) {
        this.userService = userService;
    }

    // Return type note: Map<String, Object>, not Object. See DiscoveryTools for the full
    // explanation — Spring AI 1.0.0 ignores @Tool methods whose declared return type is
    // raw java.lang.Object because its isFunctionalType() check misclassifies them.

    @Tool(name = "get_current_user",
            description = "Look up the currently logged-in user's profile (id, name, email). Use when the user asks 'who am I' or when you need their name to personalise a reply.")
    public Map<String, Object> getCurrentUser() {
        Long userId = ChatRequestContext.getUserId();
        if (userId == null) {
            // Should never happen — the orchestrator always sets userId before calling —
            // but fail loudly rather than returning a silent null profile.
            return Map.of("error", "No authenticated user in context.");
        }
        try {
            User user = userService.getUser(userId);
            UserProfile profile = new UserProfile(user.getId(), user.getName(), user.getEmail());
            return Map.of("user", profile);
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * PII-safe projection of a {@link User} suitable for sending to the LLM.
     * Password hash, booking history, audit timestamps — all deliberately absent.
     */
    public record UserProfile(Long id, String name, String email) {}
}
