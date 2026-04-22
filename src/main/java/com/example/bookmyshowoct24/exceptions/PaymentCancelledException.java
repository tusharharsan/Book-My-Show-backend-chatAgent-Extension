package com.example.bookmyshowoct24.exceptions;

public class PaymentCancelledException extends RuntimeException {
    public PaymentCancelledException(String message) {
        super(message);
    }
}
