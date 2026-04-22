package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.models.Booking;
import com.example.bookmyshowoct24.services.BookingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CancelBookingTool implements AgentTool {

    /*
     * Typed shape of the JSON input Claude sends us for this tool.
     */
    public record Input(Long bookingId) {}

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public CancelBookingTool(BookingService bookingService, ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "cancel_booking";
    }

    @Override
    public String getDescription() {
        return "Cancel an existing booking. Fails if the show starts in less than 60 minutes. " +
                "Ask the user to confirm before calling — cancellation frees the seats immediately.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "bookingId", Map.of("type", "integer", "description", "The booking's ID")
                ),
                "required", List.of("bookingId")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        Input in = objectMapper.convertValue(input, Input.class);
        Booking booking = bookingService.cancelBooking(in.bookingId());
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
