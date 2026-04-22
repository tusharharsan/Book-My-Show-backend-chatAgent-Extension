package com.example.bookmyshowoct24.controllers;

import com.example.bookmyshowoct24.dtos.CreateBookingRequestDto;
import com.example.bookmyshowoct24.dtos.CreateBookingResponseDto;
import com.example.bookmyshowoct24.dtos.ResponseStatus;
import com.example.bookmyshowoct24.models.Booking;
import com.example.bookmyshowoct24.services.BookingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public CreateBookingResponseDto createBooking(@RequestBody CreateBookingRequestDto requestDto) {
        Booking booking = bookingService.createBooking(
                requestDto.getShowSeatIds(),
                requestDto.getUserId()
        );

        CreateBookingResponseDto response = new CreateBookingResponseDto();
        response.setBooking(booking);
        response.setResponseStatus(ResponseStatus.SUCCESS);
        return response;
    }

    @DeleteMapping("/{id}")
    public CreateBookingResponseDto cancelBooking(@PathVariable Long id) {
        Booking booking = bookingService.cancelBooking(id);

        CreateBookingResponseDto response = new CreateBookingResponseDto();
        response.setBooking(booking);
        response.setResponseStatus(ResponseStatus.SUCCESS);
        return response;
    }
}
