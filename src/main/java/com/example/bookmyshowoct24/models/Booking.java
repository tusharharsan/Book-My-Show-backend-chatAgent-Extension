package com.example.bookmyshowoct24.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(name = "bookings")
public class Booking extends BaseModel {
    @ManyToOne
    private User user;

    @OneToMany
    private List<ShowSeat> showSeats;

    @Enumerated(EnumType.ORDINAL)
    private BookingStatus bookingStatus;

    private int amount;

    @OneToMany
    private List<Payment> payments;

    private Date bookedAt;
}

/*
Relationships
  Booking -- User      M : 1
  Booking -- ShowSeat  1 : M
  Booking -- Payment   1 : M

Usage with Lombok's @Builder:
  Booking booking = Booking.builder()
                           .user(user)
                           .showSeats(seats)
                           .bookingStatus(BookingStatus.PENDING)
                           .amount(500)
                           .bookedAt(new Date())
                           .build();
*/
