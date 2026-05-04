package com.example.bookmyshowoct24.ai.contracts;

import com.example.bookmyshowoct24.models.Category;
import com.example.bookmyshowoct24.models.Language;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


@JsonClassDescription("Contracts used by the Discovery tools (movie search, show listing, seat availability).")
public final class DiscoveryContracts {

    private DiscoveryContracts() {
        // Utility-style holder for records; never instantiated.
    }

    /**
     * Input for {@code searchMovies} — every field is optional. When the user
     * says "any action movies in Mumbai tonight", only {@code city} and
     * {@code category} will be populated by the LLM; the rest arrive as null.
     */
    @JsonClassDescription("Filters for finding movies currently playing. All filters are optional.")
    public record SearchMoviesRequest(

            @JsonPropertyDescription("Movie title — partial match, case insensitive. Example: 'Pushpa' or 'dune'.")
            String name,

            @JsonPropertyDescription("City to restrict the search to, e.g. 'Mumbai', 'Bengaluru'. Omit for nationwide.")
            String city,

            @JsonPropertyDescription("Movie language. Allowed values: HINDI, ENGLISH, TAMIL, TELUGU, KANNADA, MARATHI.")
            Language language,

            @JsonPropertyDescription("Movie genre. Allowed values: ACTION, COMEDY, DRAMA, HORROR, THRILLER, ROMANCE, ANIMATION.")
            Category category,

            @JsonPropertyDescription("Minimum IMDb-style rating on a 0-10 scale. Example: 7.5 to hide poorly-rated films.")
            Double minRating
    ) {}

    /**
     * Input for {@code getShows} — lists showtimes for ONE specific movie the
     * user has already chosen. {@code movieId} is required; the other fields
     * narrow the result.
     */
    @JsonClassDescription("Request showtimes for a specific movie, optionally filtered by city and date.")
    public record GetShowsRequest(

            @JsonPropertyDescription("The movie's numeric ID returned by a previous searchMovies call. Required.")
            Long movieId,

            @JsonPropertyDescription("Optional city filter, e.g. 'Mumbai'. Leave null to include all cities.")
            String city,

            @JsonPropertyDescription("Optional calendar date in YYYY-MM-DD format, e.g. '2026-04-25'. Leave null for any date.")
            String date
    ) {}

    /**
     * Input for {@code getShowSeats} — pulls the seat map for a chosen show
     * so BookBot can suggest available seats before creating a booking.
     */
    @JsonClassDescription("Request the full seat map (seat numbers, types, AVAILABLE/BLOCKED/BOOKED status) for a single show.")
    public record GetShowSeatsRequest(

            @JsonPropertyDescription("The show's numeric ID returned by a previous getShows call. Required.")
            Long showId
    ) {}
}
