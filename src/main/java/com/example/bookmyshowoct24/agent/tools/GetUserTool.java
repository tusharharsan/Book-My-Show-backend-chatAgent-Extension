package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentContext;
import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.models.User;
import com.example.bookmyshowoct24.services.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetUserTool implements AgentTool {
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public GetUserTool(UserService userService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "get_user";
    }

    @Override
    public String getDescription() {
        return "Look up the currently logged-in user's profile. Call this when you need the " +
                "user's name or email (e.g. to personalise a greeting).";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        // No input — we always return the authenticated user from AgentContext.
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }

    // This tool takes no input — the user is always the currently-authenticated
    // one from AgentContext. No record needed.
    @Override
    public String execute(Map<String, Object> input) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "{\"error\": \"no authenticated user in context\"}";
        }
        User user = userService.getUser(userId);
        return toJson(user);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
