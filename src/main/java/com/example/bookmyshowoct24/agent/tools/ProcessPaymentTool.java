package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.models.Payment;
import com.example.bookmyshowoct24.models.PaymentMode;
import com.example.bookmyshowoct24.services.PaymentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProcessPaymentTool implements AgentTool {

    /*
     * Typed shape of the JSON input Claude sends us for this tool.
     * Jackson converts the "paymentMode" string (e.g. "UPI") into the
     * PaymentMode enum automatically using PaymentMode.valueOf.
     */
    public record Input(
            Long bookingId,
            PaymentMode paymentMode,
            int amount
    ) {}

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public ProcessPaymentTool(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "process_payment";
    }

    @Override
    public String getDescription() {
        return "Charge the user and confirm the booking. Only call this after create_booking and AFTER the user " +
                "has explicitly approved the payment amount and mode. Seats move from BLOCKED to BOOKED on success.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "bookingId", Map.of("type", "integer", "description", "The PENDING booking's ID"),
                        "paymentMode", Map.of("type", "string",
                                "enum", List.of(PaymentMode.UPI.name(), PaymentMode.CREDIT_CARD.name(),
                                        PaymentMode.DEBIT_CARD.name(), PaymentMode.NET_BANKING.name()),
                                "description", "How the user wants to pay"),
                        "amount", Map.of("type", "integer", "description", "Amount in rupees")
                ),
                "required", List.of("bookingId", "paymentMode", "amount")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        Input in = objectMapper.convertValue(input, Input.class);
        Payment payment = paymentService.processPayment(in.bookingId(), in.paymentMode(), in.amount());
        return toJson(payment);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
