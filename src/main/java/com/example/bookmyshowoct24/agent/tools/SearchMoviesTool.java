package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.models.Category;
import com.example.bookmyshowoct24.models.Language;
import com.example.bookmyshowoct24.models.Movie;
import com.example.bookmyshowoct24.services.MovieService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SearchMoviesTool implements AgentTool {

    /*
     * Typed shape of the JSON input Claude sends us for this tool.
     * Any field may be null (all filters are optional). Jackson converts
     * the Map<String, Object> we get from the SDK into this record in one call.
     */
    public record Input(
            String name,
            String city,
            Language language,
            Category category,
            Double minRating
    ) {}

    private final MovieService movieService;
    private final ObjectMapper objectMapper;

    public SearchMoviesTool(MovieService movieService, ObjectMapper objectMapper) {
        this.movieService = movieService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "search_movies";
    }

    @Override
    public String getDescription() {
        return "Find movies currently playing. All filters are optional — pass only the ones the user mentioned. " +
                "Use name for title lookups ('show me Pushpa 2'), or use the other filters for discovery " +
                "('any good action movies in Hindi in Mumbai?').";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description",
                                "Movie title — partial match, case insensitive"),
                        "city", Map.of("type", "string", "description", "City name, e.g. Mumbai"),
                        "language", Map.of("type", "string", "enum", enumNames(Language.values()),
                                "description", "Language of the movie"),
                        "category", Map.of("type", "string", "enum", enumNames(Category.values()),
                                "description", "Genre/category"),
                        "minRating", Map.of("type", "number", "description", "Minimum rating, e.g. 7.0")
                ),
                "required", List.of()
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        Input in = objectMapper.convertValue(input, Input.class);
        List<Movie> movies = movieService.searchMovies(
                in.name(), in.city(), in.language(), in.category(), in.minRating());
        return toJson(movies);
    }

    private List<String> enumNames(Enum<?>[] values) {
        List<String> names = new ArrayList<>();
        for (Enum<?> value : values) {
            names.add(value.name());
        }
        return names;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
