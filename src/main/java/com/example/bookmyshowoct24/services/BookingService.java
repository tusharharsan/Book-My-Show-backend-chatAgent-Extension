package com.example.bookmyshowoct24.services;

import com.example.bookmyshowoct24.exceptions.BookingNotFoundException;
import com.example.bookmyshowoct24.exceptions.CancellationWindowClosedException;
import com.example.bookmyshowoct24.exceptions.SeatNotAvailableException;
import com.example.bookmyshowoct24.exceptions.UserNotFoundException;
import com.example.bookmyshowoct24.models.*;
import com.example.bookmyshowoct24.repositories.BookingRepository;
import com.example.bookmyshowoct24.repositories.ShowSeatRepository;
import com.example.bookmyshowoct24.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {
    private static final long CANCEL_CUTOFF_MINUTES = 60;

    private final UserRepository userRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PriceCalculatorService priceCalculatorService;
    private final BookingRepository bookingRepository;

    public BookingService(UserRepository userRepository,
                          ShowSeatRepository showSeatRepository,
                          PriceCalculatorService priceCalculatorService,
                          BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.showSeatRepository = showSeatRepository;
        this.priceCalculatorService = priceCalculatorService;
        this.bookingRepository = bookingRepository;
    }

    /*
     * Booking flow:
     *   1. Find user.
     *   2. Load requested ShowSeats; verify every seat is AVAILABLE.
     *   3. Flip seats to BLOCKED and save (inside a SERIALIZABLE transaction, so
     *      two users can't both grab the same seat).
     *   4. Create Booking with status PENDING — seats move to BOOKED after payment.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Booking createBooking(List<Long> showSeatIds, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));

        List<ShowSeat> showSeats = showSeatRepository.findAllById(showSeatIds);

        for (ShowSeat showSeat : showSeats) {
            if (showSeat.getShowSeatStatus() != ShowSeatStatus.AVAILABLE) {
                throw new SeatNotAvailableException(
                        "Seat " + showSeat.getSeat().getId() + " for show " + showSeat.getShow().getId()
                                + " is not available (status: " + showSeat.getShowSeatStatus() + ").");
            }
        }

        List<ShowSeat> savedShowSeats = new ArrayList<>();
        for (ShowSeat showSeat : showSeats) {
            showSeat.setShowSeatStatus(ShowSeatStatus.BLOCKED);
            savedShowSeats.add(showSeatRepository.save(showSeat));
        }

        Booking booking = Booking.builder()
                .user(user)
                .showSeats(savedShowSeats)
                .bookingStatus(BookingStatus.PENDING)
                .amount(priceCalculatorService.calculatePrice(savedShowSeats))
                .bookedAt(new Date())
                .payments(new ArrayList<>())
                .build();

        return bookingRepository.save(booking);
    }

    /*
     * Cancellation rules (from PDF):
     *   - Must be more than 60 minutes before the show starts.
     *   - Seats return to AVAILABLE so other users can book them.
     */
    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking " + bookingId + " not found"));

        Show show = booking.getShowSeats().get(0).getShow();
        long minutesUntilShow = (show.getStartTime().getTime() - System.currentTimeMillis()) / 60_000;
        if (minutesUntilShow < CANCEL_CUTOFF_MINUTES) {
            throw new CancellationWindowClosedException(
                    "Cannot cancel: show starts in " + minutesUntilShow + " minutes (cutoff is "
                            + CANCEL_CUTOFF_MINUTES + " minutes).");
        }

        for (ShowSeat seat : booking.getShowSeats()) {
            showSeatRepository.updateSeatStatus(ShowSeatStatus.AVAILABLE, seat.getId());
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }
}
