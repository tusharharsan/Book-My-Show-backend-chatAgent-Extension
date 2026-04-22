package com.example.bookmyshowoct24.services;

import com.example.bookmyshowoct24.exceptions.InvalidCouponException;
import com.example.bookmyshowoct24.models.Coupon;
import com.example.bookmyshowoct24.repositories.CouponRepository;
import org.springframework.stereotype.Service;

@Service
public class CouponService {
    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /*
     * Applies a coupon to the given amount and returns the discount value
     * (not the new total). Example:
     *   applyCoupon("FIRST50", 1000) with 10% off, no cap -> returns 100.
     *   Caller computes: total = 1000 - 100 = 900.
     */
    public int applyCoupon(String code, int amount) {
        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new InvalidCouponException("Coupon not found: " + code));

        if (!coupon.isActive()) {
            throw new InvalidCouponException("Coupon " + code + " is not active.");
        }

        int discount = (amount * coupon.getDiscountPercent()) / 100;
        if (coupon.getMaxDiscount() > 0 && discount > coupon.getMaxDiscount()) {
            discount = coupon.getMaxDiscount();
        }
        return discount;
    }
}
