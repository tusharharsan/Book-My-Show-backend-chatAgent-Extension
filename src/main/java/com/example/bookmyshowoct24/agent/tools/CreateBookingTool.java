package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentContext;
import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.models.Booking;
import com.example.bookmyshowoct24.services.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CreateBookingTool implements AgentTool {

    /*
     * Typed shape of the JSON input Claude sends us for this tool.
     *
     * Note: NO userId field here. The authenticated userId is read from
     * AgentContext inside execute() — this prevents a prompt-injected LLM
     * from booking tickets on someone else's account.
     */
    public record Input(List<Long> showSeatIds) {}

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public CreateBookingTool(BookingService bookingService, ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "create_booking";
    }

    @Override
    public String getDescription() {
        return "Reserve seats for the current logged-in user by creating a PENDING booking. " +
                "Seats become BLOCKED so other users can't grab them. Always confirm with the user " +
                "before calling this tool — it takes seats off the market.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "showSeatIds", Map.of("type", "array",
                                "items", Map.of("type", "integer"),
                                "description", "List of ShowSeat IDs to book")
                ),
                "required", List.of("showSeatIds")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        Long userId = AgentContext.getUserId();
        if (userId == null) {
            return "{\"error\": \"no authenticated user in context\"}";
        }
        Input in = objectMapper.convertValue(input, Input.class);
        Booking booking = bookingService.createBooking(in.showSeatIds(), userId);
        return toJson(booking);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
