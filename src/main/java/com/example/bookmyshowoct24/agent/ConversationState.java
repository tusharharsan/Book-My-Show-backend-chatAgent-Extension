package com.example.bookmyshowoct24.agent;

/*
 * States a booking conversation can be in.
 *
 * State transitions (driven by successful tool calls):
 *
 *   IDLE
 *    │   (create_booking succeeds)
 *    ▼
 *   BOOKING_PENDING
 *    │   (process_payment succeeds)
 *    ▼
 *   BOOKING_CONFIRMED
 *    │   (cancel_booking succeeds -OR- user starts a new booking)
 *    └─> IDLE
 *
 * Read-only tools (search, get_shows, get_show_seats, apply_coupon, get_user)
 * are allowed in every state — they don't mutate anything.
 */
public enum ConversationState {
    IDLE,
    BOOKING_PENDING,
    BOOKING_CONFIRMED
}
