package com.example.bookmyshowoct24.exceptions;

public class PaymentPendingException extends RuntimeException {
    public PaymentPendingException(String message) {
        super(message);
    }
}
