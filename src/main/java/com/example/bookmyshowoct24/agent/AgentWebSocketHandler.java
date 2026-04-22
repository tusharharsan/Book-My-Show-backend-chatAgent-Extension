package com.example.bookmyshowoct24.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/*
 * Handles a single WebSocket chat session between a user and the agent.
 *
 * Message protocol (JSON):
 *   client -> server: {"userId": 1, "content": "I want to watch a movie in Mumbai"}
 *   server -> client: {"sessionId": "...", "content": "Here's what's playing..."}
 *
 * Both directions are modelled as typed records below (ChatRequest / ChatResponse),
 * and Jackson handles the JSON <-> record conversion. No manual JsonNode plumbing.
 */
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    /*
     * Incoming message shape. Jackson deserialises the client's JSON into this.
     */
    public record ChatRequest(Long userId, String content) {}

    /*
     * Outgoing message shape. @JsonInclude(NON_NULL) so we can omit error-only
     * fields later without changing this class.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatResponse(String sessionId, String content) {}

    private final AgentService agentService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public AgentWebSocketHandler(AgentService agentService,
                                 SessionManager sessionManager,
                                 ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ChatResponse greeting = new ChatResponse(
                session.getId(),
                "Hi! I'm BookBot. Tell me what you'd like to watch and I'll find it for you."
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(greeting)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatRequest request = objectMapper.readValue(message.getPayload(), ChatRequest.class);

        String reply;
        try {
            reply = agentService.processMessage(session.getId(), request.userId(), request.content());
        } catch (Exception e) {
            reply = "Something went wrong: " + e.getMessage();
        }

        ChatResponse response = new ChatResponse(session.getId(), reply);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.endSession(session.getId());
    }
}
