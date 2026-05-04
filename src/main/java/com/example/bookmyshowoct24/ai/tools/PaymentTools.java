package com.example.bookmyshowoct24.ai.tools;

import com.example.bookmyshowoct24.chat.ChatRequestContext;
import com.example.bookmyshowoct24.chat.ConversationState;
import com.example.bookmyshowoct24.chat.SessionManager;
import com.example.bookmyshowoct24.chat.ToolGuardService;
import com.example.bookmyshowoct24.ai.contracts.PaymentContracts;
import com.example.bookmyshowoct24.models.Payment;
import com.example.bookmyshowoct24.services.CouponService;
import com.example.bookmyshowoct24.services.PaymentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tools facade for the Payment bounded context.
 *
 * <p>Two tools live here: {@code process_payment} (a mutation — actually
 * charges the user and confirms the booking) and {@code apply_coupon}
 * (read-only — just calculates what a coupon would give). So only the first
 * one goes through the state-machine guard; the coupon check can run any time
 * the user is curious about the discount.
 *
 * <p>Money field note: notice that {@code ApplyCouponRequest.amount()} and
 * {@code ProcessPaymentRequest.amount()} are both {@code int}, not
 * {@code double}. A floating-point rupee amount is a nightmare waiting to
 * happen — the LLM might send 499.99999999 — so we lock the schema to
 * integers and make the model round up front.
 */
@Component
public class PaymentTools {

    private final PaymentService paymentService;
    private final CouponService couponService;
    private final SessionManager sessionManager;
    private final ToolGuardService toolGuard;

    public PaymentTools(PaymentService paymentService,
                        CouponService couponService,
                        SessionManager sessionManager,
                        ToolGuardService toolGuard) {
        this.paymentService = paymentService;
        this.couponService = couponService;
        this.sessionManager = sessionManager;
        this.toolGuard = toolGuard;
    }

    // Return type note: Map<String, Object>, not Object. See DiscoveryTools for the full
    // explanation — Spring AI 1.0.0 ignores @Tool methods whose declared return type is
    // raw java.lang.Object because its isFunctionalType() check misclassifies them.

    @Tool(name = "process_payment",
            description = "Charge a PENDING booking and confirm it. Only valid after create_booking has succeeded. Use apply_coupon first if the user wants a discount.")
    public Map<String, Object> processPayment(PaymentContracts.ProcessPaymentRequest request) {
        String sessionId = ChatRequestContext.getSessionId();
        ConversationState state = sessionManager.getState(sessionId);

        ToolGuardService.GuardResult guard = toolGuard.check("process_payment", state);
        if (!guard.allowed()) {
            return Map.of("error", guard.reason());
        }

        try {
            Payment payment = paymentService.processPayment(
                    request.bookingId(),
                    request.paymentMode(),
                    request.amount()
            );
            sessionManager.setState(sessionId, toolGuard.nextState("process_payment", state));
            return Map.of("payment", payment);
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }

    @Tool(name = "apply_coupon",
            description = "Validate a coupon code and compute the discount it would give on the current booking amount. Does not modify the booking — safe to call any time.")
    public Map<String, Object> applyCoupon(PaymentContracts.ApplyCouponRequest request) {
        // No guard check: apply_coupon is read-only and allowed in any state.
        try {
            int discount = couponService.applyCoupon(request.code(), request.amount());
            CouponResult result = new CouponResult(
                    request.code(),
                    request.amount(),
                    discount,
                    request.amount() - discount
            );
            return Map.of("result", result);
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Shape of the apply_coupon success response sent back to the LLM.
     * We don't return the raw {@code int} discount — a typed record with
     * named fields gives the model much clearer signal about what each
     * number means, which improves how it narrates the result to the user.
     */
    public record CouponResult(
            String code,
            int originalAmount,
            int discount,
            int finalAmount
    ) {}
}
