package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.models.ShowSeat;
import com.example.bookmyshowoct24.services.ShowService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetShowSeatsTool implements AgentTool {

    /*
     * Typed shape of the JSON input Claude sends us for this tool.
     */
    public record Input(Long showId) {}

    private final ShowService showService;
    private final ObjectMapper objectMapper;

    public GetShowSeatsTool(ShowService showService, ObjectMapper objectMapper) {
        this.showService = showService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "get_show_seats";
    }

    @Override
    public String getDescription() {
        return "Get the seat map for a show, including which seats are AVAILABLE, BLOCKED, or BOOKED. " +
                "Use this before booking so the user can choose seats.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "showId", Map.of("type", "integer", "description", "The show ID")
                ),
                "required", List.of("showId")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        Input in = objectMapper.convertValue(input, Input.class);
        List<ShowSeat> seats = showService.getShowSeats(in.showId());
        return toJson(seats);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
