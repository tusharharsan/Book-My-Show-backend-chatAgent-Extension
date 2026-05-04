package com.example.bookmyshowoct24.ai.tools;

import com.example.bookmyshowoct24.ai.contracts.DiscoveryContracts;
import com.example.bookmyshowoct24.models.Movie;
import com.example.bookmyshowoct24.models.Show;
import com.example.bookmyshowoct24.models.ShowSeat;
import com.example.bookmyshowoct24.services.MovieService;
import com.example.bookmyshowoct24.services.ShowService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * Tools facade for the Discovery bounded context.
 *
 * <p>This is a pure bridge class — it owns no business logic of its own.
 * Every {@code @Tool}-annotated method simply unpacks a typed request record
 * from {@code DiscoveryContracts}, calls the matching legacy service method,
 * and returns the result. That is the heart of the "Do No Harm" philosophy:
 * {@link MovieService} and {@link ShowService} have no idea an AI exists.
 *
 * <p>All three tools here are read-only — they list things, they don't change
 * anything. So unlike the Ticketing / Payment tools, they don't need
 * {@code ToolGuardService} state-machine checks.
 *
 * <p>Tool names use snake_case (e.g. {@code search_movies}) instead of the
 * Java method name for two reasons:
 * <ol>
 *   <li>Consistency with the names the old hand-rolled tools used, so any
 *       existing prompt examples / test cases keep working.</li>
 *   <li>Snake_case is what most LLM providers' own built-in tools use, so
 *       Claude/Gemini/GPT handle this casing with the highest reliability.</li>
 * </ol>
 */
@Component
public class DiscoveryTools {

    private final MovieService movieService;
    private final ShowService showService;

    public DiscoveryTools(MovieService movieService, ShowService showService) {
        this.movieService = movieService;
        this.showService = showService;
    }

    // Return type note: we use Map<String, Object> — NOT Object — because Spring AI 1.0.0
    // has a check in MethodToolCallbackProvider#isFunctionalType that uses
    // ClassUtils.isAssignable(returnType, Function.class). That check evaluates to true
    // when the return type is java.lang.Object (since Function IS-A Object), so any
    // @Tool method declared to return Object is silently dropped with the warning
    // "…returns a functional type. This is not supported…". Using Map<String, Object>
    // side-steps the quirk and lets us keep the same success/error-map pattern.

    @Tool(name = "search_movies",
            description = "Find movies currently playing. All filters are optional — pass only what the user mentioned.")
    public Map<String, Object> searchMovies(DiscoveryContracts.SearchMoviesRequest request) {
        try {
            List<Movie> movies = movieService.searchMovies(
                    request.name(),
                    request.city(),
                    request.language(),
                    request.category(),
                    request.minRating()
            );
            return Map.of("movies", movies);
        } catch (RuntimeException e) {
            // Convert exceptions into tool-result errors so the LLM sees them and can
            // explain to the user, rather than bubbling up and crashing the chat turn.
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "get_shows",
            description = "List showtimes for a specific movie. movieId is required; city and date (YYYY-MM-DD) are optional filters.")
    public Map<String, Object> getShows(DiscoveryContracts.GetShowsRequest request) {
        try {
            java.util.Date parsedDate = null;
            if (request.date() != null && !request.date().isBlank()) {
                // The LLM sends dates as "YYYY-MM-DD" strings because that is what the
                // contract's @JsonPropertyDescription tells it to do. We parse here —
                // not in ShowService — so the legacy service keeps its java.util.Date API.
                parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(request.date());
            }
            List<Show> shows = showService.getShows(request.movieId(), request.city(), parsedDate);
            return Map.of("shows", shows);
        } catch (ParseException e) {
            return Map.of("error", "Could not parse date '" + request.date() + "'. Expected format: YYYY-MM-DD.");
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "get_show_seats",
            description = "Get the seat map for a show: which seats are AVAILABLE, BLOCKED, or BOOKED, plus seat type and price tier.")
    public Map<String, Object> getShowSeats(DiscoveryContracts.GetShowSeatsRequest request) {
        try {
            List<ShowSeat> seats = showService.getShowSeats(request.showId());
            return Map.of("seats", seats);
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }
}
