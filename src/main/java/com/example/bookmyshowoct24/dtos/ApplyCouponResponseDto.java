package com.example.bookmyshowoct24.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplyCouponResponseDto {
    private String code;
    private int originalAmount;
    private int discount;
    private int finalAmount;
}
