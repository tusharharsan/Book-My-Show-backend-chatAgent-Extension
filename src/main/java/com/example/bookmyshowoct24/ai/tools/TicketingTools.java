package com.example.bookmyshowoct24.ai.tools;

import com.example.bookmyshowoct24.chat.ChatRequestContext;
import com.example.bookmyshowoct24.chat.ConversationState;
import com.example.bookmyshowoct24.chat.SessionManager;
import com.example.bookmyshowoct24.chat.ToolGuardService;
import com.example.bookmyshowoct24.ai.contracts.TicketingContracts;
import com.example.bookmyshowoct24.models.Booking;
import com.example.bookmyshowoct24.services.BookingService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tools facade for the Ticketing bounded context — creating and cancelling
 * bookings. Both tools in this class are <strong>mutating</strong>, so unlike
 * the Discovery tools each method follows the same three-step pattern:
 *
 * <ol>
 *   <li><strong>Guard</strong> — ask {@link ToolGuardService} whether this
 *       tool is allowed in the session's current state. This blocks e.g.
 *       "create a second booking while the first is still PENDING". If the
 *       guard says no, we return a {@code {"error": ...}} map instead of
 *       mutating anything; the LLM sees the message and apologises to the user.</li>
 *
 *   <li><strong>Delegate</strong> — call the existing {@link BookingService}
 *       which owns all the real logic (SERIALIZABLE transactions, cancellation
 *       windows, seat-status transitions). We change zero lines in that service.</li>
 *
 *   <li><strong>Advance state</strong> — ONLY after a successful call do we
 *       move the conversation state forward (IDLE → BOOKING_PENDING, etc).
 *       Failures leave state untouched so the user can retry.</li>
 * </ol>
 *
 * <p>Note how {@code userId} is read from {@link ChatRequestContext}, not from the
 * tool's input schema. This is a deliberate prompt-injection defence — the
 * LLM never gets the chance to say "book these seats for user 99".
 */
@Component
public class TicketingTools {

    private final BookingService bookingService;
    private final SessionManager sessionManager;
    private final ToolGuardService toolGuard;

    public TicketingTools(BookingService bookingService,
                          SessionManager sessionManager,
                          ToolGuardService toolGuard) {
        this.bookingService = bookingService;
        this.sessionManager = sessionManager;
        this.toolGuard = toolGuard;
    }

    // Return type note: Map<String, Object>, not Object. See DiscoveryTools for the full
    // explanation — Spring AI 1.0.0 ignores @Tool methods whose declared return type is
    // raw java.lang.Object because its isFunctionalType() check misclassifies them.

    @Tool(name = "create_booking",
            description = "Reserve the given seats for the current logged-in user. Creates a PENDING booking that still needs payment. Only valid when no booking is already pending.")
    public Map<String, Object> createBooking(TicketingContracts.CreateBookingRequest request) {
        String sessionId = ChatRequestContext.getSessionId();
        ConversationState state = sessionManager.getState(sessionId);

        // 1) State-machine guard.
        ToolGuardService.GuardResult guard = toolGuard.check("create_booking", state);
        if (!guard.allowed()) {
            return Map.of("error", guard.reason());
        }

        // 2) Delegate — note userId comes from ChatRequestContext, not from the request.
        try {
            Booking booking = bookingService.createBooking(request.showSeatIds(), ChatRequestContext.getUserId());
            // 3) Advance state only on success.
            sessionManager.setState(sessionId, toolGuard.nextState("create_booking", state));
            return Map.of("booking", booking);
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "cancel_booking",
            description = "Cancel a previously-created booking. Fails automatically if the show starts in under 60 minutes.")
    public Map<String, Object> cancelBooking(TicketingContracts.CancelBookingRequest request) {
        String sessionId = ChatRequestContext.getSessionId();
        ConversationState state = sessionManager.getState(sessionId);

        ToolGuardService.GuardResult guard = toolGuard.check("cancel_booking", state);
        if (!guard.allowed()) {
            return Map.of("error", guard.reason());
        }

        try {
            Booking booking = bookingService.cancelBooking(request.bookingId());
            sessionManager.setState(sessionId, toolGuard.nextState("cancel_booking", state));
            return Map.of("booking", booking);
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }
}
