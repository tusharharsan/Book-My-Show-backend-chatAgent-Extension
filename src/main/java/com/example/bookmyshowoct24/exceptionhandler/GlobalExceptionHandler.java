package com.example.bookmyshowoct24.exceptionhandler;

import com.example.bookmyshowoct24.dtos.ErrorResponse;
import com.example.bookmyshowoct24.exceptions.BookingNotFoundException;
import com.example.bookmyshowoct24.exceptions.CancellationWindowClosedException;
import com.example.bookmyshowoct24.exceptions.InvalidCouponException;
import com.example.bookmyshowoct24.exceptions.PaymentCancelledException;
import com.example.bookmyshowoct24.exceptions.PaymentFailedException;
import com.example.bookmyshowoct24.exceptions.PaymentPendingException;
import com.example.bookmyshowoct24.exceptions.SeatNotAvailableException;
import com.example.bookmyshowoct24.exceptions.ShowNotFoundException;
import com.example.bookmyshowoct24.exceptions.ShowSeatNotFoundException;
import com.example.bookmyshowoct24.exceptions.UserAlreadyExistsException;
import com.example.bookmyshowoct24.exceptions.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "User Not Found", ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserExists(UserAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, "User Already Exists", ex.getMessage());
    }

    @ExceptionHandler(ShowNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShowNotFound(ShowNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Show Not Found", ex.getMessage());
    }

    @ExceptionHandler(ShowSeatNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShowSeatNotFound(ShowSeatNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Show Seat Not Found", ex.getMessage());
    }

    @ExceptionHandler(SeatNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleSeatNotAvailable(SeatNotAvailableException ex) {
        // 409 CONFLICT: the seat exists but someone else has it or it's blocked.
        return build(HttpStatus.CONFLICT, "Seat Not Available", ex.getMessage());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentFailed(PaymentFailedException ex) {
        return build(HttpStatus.PAYMENT_REQUIRED, "Payment Failed", ex.getMessage());
    }

    @ExceptionHandler(PaymentPendingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentPending(PaymentPendingException ex) {
        return build(HttpStatus.ACCEPTED, "Payment Pending", ex.getMessage());
    }

    @ExceptionHandler(PaymentCancelledException.class)
    public ResponseEntity<ErrorResponse> handlePaymentCancelled(PaymentCancelledException ex) {
        return build(HttpStatus.PAYMENT_REQUIRED, "Payment Cancelled", ex.getMessage());
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotFound(BookingNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Booking Not Found", ex.getMessage());
    }

    @ExceptionHandler(CancellationWindowClosedException.class)
    public ResponseEntity<ErrorResponse> handleCancellationWindowClosed(CancellationWindowClosedException ex) {
        return build(HttpStatus.BAD_REQUEST, "Cancellation Window Closed", ex.getMessage());
    }

    @ExceptionHandler(InvalidCouponException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCoupon(InvalidCouponException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid Coupon", ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message) {
        ErrorResponse body = new ErrorResponse(status.value(), error, message);
        return ResponseEntity.status(status).body(body);
    }
}
