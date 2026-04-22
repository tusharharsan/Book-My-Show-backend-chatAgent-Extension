package com.example.bookmyshowoct24.agent.tools;

import com.example.bookmyshowoct24.agent.AgentTool;
import com.example.bookmyshowoct24.services.CouponService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ApplyCouponTool implements AgentTool {

    /*
     * Typed shape of the JSON input Claude sends us for this tool.
     */
    public record Input(
            String code,
            int amount
    ) {}

    private final CouponService couponService;
    private final ObjectMapper objectMapper;

    public ApplyCouponTool(CouponService couponService, ObjectMapper objectMapper) {
        this.couponService = couponService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "apply_coupon";
    }

    @Override
    public String getDescription() {
        return "Check if a coupon code is valid and return the discount it would give on the booking amount. " +
                "Call this when the user offers a promo code at checkout.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "description", "Coupon code, e.g. FIRST50"),
                        "amount", Map.of("type", "integer", "description", "Current booking amount in rupees")
                ),
                "required", List.of("code", "amount")
        );
    }

    @Override
    public String execute(Map<String, Object> input) {
        Input in = objectMapper.convertValue(input, Input.class);
        int discount = couponService.applyCoupon(in.code(), in.amount());
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "code", in.code(),
                    "originalAmount", in.amount(),
                    "discount", discount,
                    "finalAmount", in.amount() - discount
            ));
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization failed: " + e.getMessage() + "\"}";
        }
    }
}
