package com.example.bookmyshowoct24.ai.contracts;

import com.example.bookmyshowoct24.models.PaymentMode;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Domain contracts for the "Payment" bounded context — charging a PENDING
 * booking and applying coupon discounts. These contracts feed the
 * {@code PaymentTools} facade.
 *
 * <p>Notice that {@code ProcessPaymentRequest.amount} is a plain {@code int}
 * in rupees. We deliberately do NOT accept a floating-point value: letting
 * an LLM pass "499.9999999" or "1e3" into a money field is a footgun. By
 * declaring the field as an int + describing it as "amount in whole rupees"
 * in the schema, we force the model to round / normalise BEFORE calling us.
 */
@JsonClassDescription("Contracts used by the Payment tools (process payment, validate coupon).")
public final class PaymentContracts {

    private PaymentContracts() {}

    /**
     * Input for {@code processPayment}. All three fields are required — we
     * need to know WHICH booking to charge, HOW the user is paying, and
     * HOW MUCH (which may already include a coupon discount computed on a
     * prior applyCoupon call).
     */
    @JsonClassDescription("Charge a PENDING booking and mark it CONFIRMED. Only valid once a booking has been created.")
    public record ProcessPaymentRequest(

            @JsonPropertyDescription("The PENDING booking's numeric ID, from a prior createBooking call. Required.")
            Long bookingId,

            @JsonPropertyDescription("Payment method. Allowed values: UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING. Required.")
            PaymentMode paymentMode,

            @JsonPropertyDescription("Amount to charge in whole Indian rupees (no decimals, no currency symbol). Required.")
            int amount
    ) {}

    /**
     * Input for {@code applyCoupon}. This tool does NOT persist anything —
     * it just computes the discount the coupon would give and returns the
     * revised total, so the AI can show the user a price preview before
     * the actual processPayment call.
     */
    @JsonClassDescription("Validate a coupon code and compute the discount it would give on the current booking amount. Read-only — does not modify the booking.")
    public record ApplyCouponRequest(

            @JsonPropertyDescription("The coupon code the user typed. Case sensitive. Example: 'FIRST50'. Required.")
            String code,

            @JsonPropertyDescription("Current booking amount in whole Indian rupees, BEFORE the coupon is applied. Required.")
            int amount
    ) {}
}
