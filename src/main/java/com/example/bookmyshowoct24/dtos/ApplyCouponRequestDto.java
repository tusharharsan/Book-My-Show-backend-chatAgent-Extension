package com.example.bookmyshowoct24.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplyCouponRequestDto {
    private String code;
    private int amount;
}
