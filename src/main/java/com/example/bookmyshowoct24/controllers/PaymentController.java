package com.example.bookmyshowoct24.controllers;

import com.example.bookmyshowoct24.dtos.PaymentRequestDto;
import com.example.bookmyshowoct24.dtos.PaymentResponseDto;
import com.example.bookmyshowoct24.dtos.ResponseStatus;
import com.example.bookmyshowoct24.models.Payment;
import com.example.bookmyshowoct24.services.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // POST /api/payments  {bookingId, paymentMode, amount}
    @PostMapping
    public PaymentResponseDto processPayment(@RequestBody PaymentRequestDto request) {
        Payment payment = paymentService.processPayment(
                request.getBookingId(),
                request.getPaymentMode(),
                request.getAmount()
        );
        PaymentResponseDto response = new PaymentResponseDto();
        response.setPayment(payment);
        response.setResponseStatus(ResponseStatus.SUCCESS);
        return response;
    }
}
