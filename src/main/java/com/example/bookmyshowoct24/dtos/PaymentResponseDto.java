package com.example.bookmyshowoct24.dtos;

import com.example.bookmyshowoct24.models.Payment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentResponseDto {
    private Payment payment;
    private ResponseStatus responseStatus;
}
