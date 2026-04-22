package com.example.bookmyshowoct24.services;

import com.example.bookmyshowoct24.exceptions.PaymentFailedException;
import com.example.bookmyshowoct24.models.*;
import com.example.bookmyshowoct24.repositories.BookingRepository;
import com.example.bookmyshowoct24.repositories.PaymentRepository;
import com.example.bookmyshowoct24.repositories.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          BookingRepository bookingRepository,
                          ShowSeatRepository showSeatRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
    }

    /*
     * Simulates a payment gateway call.
     * Real flow for this method:
     *   1. Find the booking (must be PENDING).
     *   2. Create a Payment row with CONFIRMED status.
     *   3. Attach payment to booking, move booking -> CONFIRMED.
     *   4. Move each ShowSeat from BLOCKED -> BOOKED.
     *
     * A real system would call Razorpay / Stripe here and only proceed on success.
     */
    @Transactional
    public Payment processPayment(Long bookingId, PaymentMode paymentMode, int amount) {
        Optional<Booking> optionalBooking = bookingRepository.findById(bookingId);
        if (optionalBooking.isEmpty()) {
            throw new PaymentFailedException("Booking not found for id: " + bookingId);
        }
        Booking booking = optionalBooking.get();

        Payment payment = new Payment();
        payment.setReferenceNumber(UUID.randomUUID().toString());
        payment.setAmount(amount);
        payment.setPaymentMode(paymentMode);
        payment.setPaymentStatus(PaymentStatus.CONFIRMED);
        Payment savedPayment = paymentRepository.save(payment);

        List<Payment> payments = booking.getPayments() != null
                ? booking.getPayments()
                : new ArrayList<>();
        payments.add(savedPayment);
        booking.setPayments(payments);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        for (ShowSeat showSeat : booking.getShowSeats()) {
            showSeatRepository.updateSeatStatus(ShowSeatStatus.BOOKED, showSeat.getId());
        }

        return savedPayment;
    }
}
