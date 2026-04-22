package com.example.bookmyshowoct24.controllers;

import com.example.bookmyshowoct24.dtos.ApplyCouponRequestDto;
import com.example.bookmyshowoct24.dtos.ApplyCouponResponseDto;
import com.example.bookmyshowoct24.services.CouponService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    // POST /api/coupons/apply  {code, amount}
    @PostMapping("/apply")
    public ApplyCouponResponseDto applyCoupon(@RequestBody ApplyCouponRequestDto request) {
        int discount = couponService.applyCoupon(request.getCode(), request.getAmount());

        ApplyCouponResponseDto response = new ApplyCouponResponseDto();
        response.setCode(request.getCode());
        response.setOriginalAmount(request.getAmount());
        response.setDiscount(discount);
        response.setFinalAmount(request.getAmount() - discount);
        return response;
    }
}
