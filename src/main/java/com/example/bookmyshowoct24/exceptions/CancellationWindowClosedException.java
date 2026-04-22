package com.example.bookmyshowoct24.exceptions;

public class CancellationWindowClosedException extends RuntimeException {
    public CancellationWindowClosedException(String message) {
        super(message);
    }
}
