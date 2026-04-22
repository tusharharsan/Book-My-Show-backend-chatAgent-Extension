package com.example.bookmyshowoct24.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "coupons")
public class Coupon extends BaseModel {
    @Column(unique = true)
    private String code;

    // 0-100. e.g. 20 means "20% off".
    private int discountPercent;

    // Optional cap so big bookings don't get huge discounts. 0 means no cap.
    private int maxDiscount;

    private boolean active;
}
