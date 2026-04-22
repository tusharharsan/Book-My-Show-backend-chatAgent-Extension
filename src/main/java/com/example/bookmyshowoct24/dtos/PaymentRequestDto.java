package com.example.bookmyshowoct24.dtos;

import com.example.bookmyshowoct24.models.PaymentMode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentRequestDto {
    private Long bookingId;
    private PaymentMode paymentMode;
    private int amount;
}
