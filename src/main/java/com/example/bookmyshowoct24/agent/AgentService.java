package com.example.bookmyshowoct24.agent;

import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.client.AnthropicClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * The heart of the AI layer.
 *
 * SDK naming note (important for students):
 *   ContentBlock       = the SDK type for what Claude RETURNS to us (response content).
 *   ContentBlockParam  = the SDK type for what we SEND BACK to Claude (request content).
 * So when the model replies with a tool_use block (ContentBlock.ToolUseBlock), we have
 * to convert it into a ToolUseBlockParam before we can echo it back in the next request.
 * That's what blockToParam() below does.
 *
 * On every incoming user message we:
 *   1. Append it to the session's conversation history.
 *   2. Call Claude with the full history + tool definitions + system prompt.
 *   3. If Claude responded with plain text -> return it.
 *   4. If Claude asked to call tools -> run each tool, append the results
 *      as a "user" message, and loop back to step 2.
 *
 * The loop ends when Claude says stop_reason = END_TURN.
 *
 * Example flow: "Book 2 seats for Pushpa 2 in Mumbai tonight"
 *   turn 0: Claude -> tool_use(search_movies, {name:"Pushpa 2", city:"Mumbai"})
 *   turn 1: Claude -> tool_use(get_shows, {movieId:42})
 *   turn 2: Claude -> tool_use(get_show_seats, {showId:101})
 *   turn 3: Claude -> text "Here are your seats... shall I reserve A1, A2?"
 *   (user replies: "yes")
 *   turn 4: Claude -> tool_use(create_booking, {showSeatIds:[501,502]})
 *   turn 5: Claude -> text "Reserved. Pay ₹1000?"  ...and so on.
 */
@Service
public class AgentService {
    private static final String SYSTEM_PROMPT = """
            You are BookBot, the booking assistant for BookMyShow — India's biggest movie and events ticketing service.

            Your job is to help users discover, book, and manage movie tickets through natural conversation.

            How to work:
            - Always call tools to fetch live data. Never invent movies, showtimes, seats, prices, or availability.
            - Take filters from context. If the user says "this weekend in Mumbai", pass city="Mumbai" to search_movies
              and filter shows by weekend dates.
            - Prices are in Indian Rupees (₹).
            - Seat types are GOLD, DIAMOND, PLATINUM. Payment modes are UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING.

            Human-in-the-loop — destructive actions require confirmation:
            - Before create_booking, summarise what will be booked and ask "shall I reserve these seats?"
            - Before process_payment, show the total (including any coupon) and ask "shall I process the payment?"
            - Before cancel_booking, ask "are you sure you want to cancel booking #N?"

            Booking flow is gated by a state machine. The correct order is:
              1. search_movies / get_shows / get_show_seats   (any time)
              2. create_booking                                (moves us to BOOKING_PENDING)
              3. apply_coupon (optional), then process_payment (moves us to BOOKING_CONFIRMED)
              4. cancel_booking is only valid after a booking has been created.
            If a tool returns an error like "cannot be called in state X", that means you're skipping a step.

            When a tool returns an error, explain it conversationally and suggest a fix. Do not retry the same call blindly.

            Style: warm, concise, and practical. Use bullet points or short tables for lists of movies/shows/seats.
            """;

    private final AnthropicClient anthropicClient;
    private final ToolRegistry toolRegistry;
    private final SessionManager sessionManager;
    private final ToolGuardService toolGuard;
    private final ObjectMapper objectMapper;
    private final String model;

    public AgentService(AnthropicClient anthropicClient,
                        ToolRegistry toolRegistry,
                        SessionManager sessionManager,
                        ToolGuardService toolGuard,
                        ObjectMapper objectMapper,
                        @Value("${anthropic.model:claude-opus-4-7}") String model) {
        this.anthropicClient = anthropicClient;
        this.toolRegistry = toolRegistry;
        this.sessionManager = sessionManager;
        this.toolGuard = toolGuard;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public String processMessage(String sessionId, Long userId, String userText) {
        AgentContext.setUserId(userId);
        try {
            return runLoop(sessionId, userId, userText);
        } finally {
            // Critical: Spring reuses threads across requests. If we don't clear
            // the ThreadLocal, the next request could silently inherit this user's id.
            AgentContext.clear();
        }
    }

    private String runLoop(String sessionId, Long userId, String userText) {
        List<MessageParam> history = sessionManager.getHistory(sessionId);

        history.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content(userText)
                .build());

        List<Tool> tools = toolRegistry.toAnthropicTools();
        // userId is in the prompt as context for the LLM's replies (e.g. so it
        // knows who the "me" is in "my bookings"), but tools that touch the user
        // MUST read it from AgentContext, not from LLM-provided input.
        String systemWithUser = SYSTEM_PROMPT + "\n\nCurrent logged-in user id: " + userId;

        // Safety cap — if the model ever loops forever, break out after 10 turns.
        for (int turn = 0; turn < 10; turn++) {
            // Thinking intentionally disabled: signed thinking blocks must be echoed back
            // verbatim across tool_use turns, which adds complexity. Tool-use works fine
            // without thinking — students can enable it later with proper block preservation.
            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .system(systemWithUser)
                    .messages(history);
            for (Tool tool : tools) {
                paramsBuilder.addTool(tool);
            }

            Message response = anthropicClient.messages().create(paramsBuilder.build());

            history.add(assistantMessageToParam(response));

            boolean wantsToolUse = false;
            if (response.stopReason().isPresent()) {
                wantsToolUse = response.stopReason().get().equals(StopReason.TOOL_USE);
            }

            if (!wantsToolUse) {
                return extractText(response);
            }

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                if (block.toolUse().isPresent()) {
                    ToolUseBlock toolUse = block.toolUse().get();
                    toolResults.add(runTool(sessionId, toolUse));
                }
            }

            history.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }

        // Hit the safety cap. History currently ends with a user(tool_results) turn,
        // which would break user->assistant alternation on the next message. Close it
        // cleanly with a synthetic assistant turn so the session stays valid.
        // (Anthropic's API requires messages to alternate: user -> assistant -> user -> ...)
        String stuckMessage = "Sorry, I seem to be stuck on this request. Could you rephrase or start fresh?";
        history.add(MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .content(stuckMessage)
                .build());
        return stuckMessage;
    }

    private ContentBlockParam runTool(String sessionId, ToolUseBlock toolUse) {
        String name = toolUse.name();
        AgentTool tool = toolRegistry.lookup(name);

        String result;
        boolean isError = false;

        if (tool == null) {
            result = "{\"error\": \"unknown tool: " + name + "\"}";
            isError = true;
        } else {
            ConversationState state = sessionManager.getState(sessionId);
            ToolGuardService.GuardResult guard = toolGuard.check(name, state);

            if (!guard.allowed()) {
                result = "{\"error\": \"" + guard.reason().replace("\"", "'") + "\"}";
                isError = true;
            } else {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> input = objectMapper.convertValue(toolUse._input(), Map.class);
                    result = tool.execute(input);
                    // Advance the state machine only on successful execution.
                    sessionManager.setState(sessionId, toolGuard.nextState(name, state));
                } catch (Exception e) {
                    result = "{\"error\": \"" + e.getClass().getSimpleName() + ": " + e.getMessage() + "\"}";
                    isError = true;
                }
            }
        }

        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(toolUse.id())
                .content(result)
                .isError(isError)
                .build());
    }

    // Convert the model's response Message into a MessageParam we can store in history
    // and echo back on the next API call.
    private MessageParam assistantMessageToParam(Message message) {
        List<ContentBlockParam> params = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            ContentBlockParam param = blockToParam(block);
            if (param != null) {
                params.add(param);
            }
        }

        return MessageParam.builder()
                .role(MessageParam.Role.ASSISTANT)
                .contentOfBlockParams(params)
                .build();
    }

    private ContentBlockParam blockToParam(ContentBlock block) {
        // Plain text from the model.
        if (block.text().isPresent()) {
            return ContentBlockParam.ofText(TextBlockParam.builder()
                    .text(block.text().get().text())
                    .build());
        }
        // A tool call the model is requesting.
        // toolUse._input() is already a JsonValue — do NOT re-wrap with JsonValue.from(...),
        // that double-wraps and the echoed input no longer matches the original schema.
        // (The underscore prefix is the Anthropic SDK's convention for raw/unparsed access.)
        if (block.toolUse().isPresent()) {
            ToolUseBlock toolUse = block.toolUse().get();
            return ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(toolUse.id())
                    .name(toolUse.name())
                    .input(toolUse._input())
                    .build());
        }
        // thinking blocks and others — skip in history.
        return null;
    }

    // Flatten the model's response to a single text string (what the user sees).
    private String extractText(Message response) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : response.content()) {
            if (block.text().isPresent()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(block.text().get().text());
            }
        }
        return sb.toString();
    }
}
