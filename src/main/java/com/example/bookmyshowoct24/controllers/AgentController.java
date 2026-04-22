package com.example.bookmyshowoct24.controllers;

import com.example.bookmyshowoct24.agent.AgentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/*
 * REST wrapper around AgentService so the agent can be tested from Postman
 * without a WebSocket client. For multi-turn chats, keep the same sessionId
 * across requests.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    // POST /api/agent/chat  {"sessionId": "...", "userId": 1, "content": "..."}
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.getOrDefault("sessionId", UUID.randomUUID().toString());
        Long userId = ((Number) body.get("userId")).longValue();
        String content = (String) body.get("content");

        String reply = agentService.processMessage(sessionId, userId, content);

        return Map.of("sessionId", sessionId, "content", reply);
    }
}
