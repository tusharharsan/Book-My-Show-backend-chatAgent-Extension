package com.example.bookmyshowoct24.ai.contracts;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Domain contracts for the "Ticketing" bounded context — reserving and
 * releasing seats. These contracts feed the {@code TicketingTools} facade,
 * which in turn calls the legacy {@code BookingService}.
 *
 * <p><strong>Security note.</strong> Notice that none of the requests below
 * contain a {@code userId} field. Accepting a userId from the LLM would be a
 * prompt-injection hole: a malicious user could say "book seats for user 99"
 * and the model would happily oblige. Instead, the Ticketing tools read the
 * authenticated user ID from {@code ChatRequestContext} (a ThreadLocal populated by
 * the orchestrator before each {@code .call()}), and the LLM never sees that
 * value in its schema. Absence of a field in a contract is itself a security
 * control.
 */
@JsonClassDescription("Contracts used by the Ticketing tools (create booking, cancel booking).")
public final class TicketingContracts {

    private TicketingContracts() {}

    /**
     * Input for {@code createBooking}. The LLM supplies the list of seat IDs
     * the user has agreed to; the authenticated user ID is injected by the
     * orchestrator, not the model.
     */
    @JsonClassDescription("Reserve a list of seats for the CURRENT logged-in user. Produces a PENDING booking that still needs payment.")
    public record CreateBookingRequest(

            @JsonPropertyDescription("Numeric ShowSeat IDs the user has agreed to. Get these from a prior getShowSeats call. Required.")
            List<Long> showSeatIds
    ) {}

    /**
     * Input for {@code cancelBooking}. Cancellation is blocked by
     * {@code BookingService} if the show starts in less than 60 minutes —
     * the tool surfaces that error back to the LLM verbatim so the assistant
     * can explain the rule to the user.
     */
    @JsonClassDescription("Cancel a previously-created booking. Fails automatically if the show starts in under 60 minutes.")
    public record CancelBookingRequest(

            @JsonPropertyDescription("The booking's numeric ID (as returned by createBooking or shown on the user's ticket). Required.")
            Long bookingId
    ) {}
}
