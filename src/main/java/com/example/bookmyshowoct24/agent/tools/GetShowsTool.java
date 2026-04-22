package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.models.Show;
import com.example.bookmyshowoct24.services.ShowService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class GetShowsTool implements AgentTool {

    /*
     * Typed shape of the JSON input Claude sends us for this tool.
     * `date` is kept as a String here because Jackson can't parse "YYYY-MM-DD"
     * into java.util.Date without extra config — we parse it manually below.
     */
    public record Input(
            Long movieId,
            String city,
            String date
    ) {}

    private final ShowService showService;
    private final ObjectMapper objectMapper;

    public GetShowsTool(ShowService showService, ObjectMapper objectMapper) {
        this.showService = showService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "get_shows";
    }

    @Override
    public String getDescription() {
        return "List show timings for a specific movie. Use this after the user has picked a movie " +
                "(e.g. 'When can I watch Pushpa 2 in Mumbai?'). date is optional (format YYYY-MM-DD).";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "movieId", Map.of("type", "integer", "description", "The movie ID (from search_movies output)"),
                        "city", Map.of("type", "string", "description", "Filter by city"),
                        "date", Map.of("type", "string", "description", "Filter by date, YYYY-MM-DD")
                ),
                "required", List.of("movieId")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        Input in = objectMapper.convertValue(input, Input.class);
        Date date = parseDate(in.date());
        List<Show> shows = showService.getShows(in.movieId(), in.city(), date);
        return toJson(shows);
    }

    private Date parseDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s);
        } catch (ParseException e) {
            return null;
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
