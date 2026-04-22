package com.example.bookmyshowoct24.repositories;

import com.example.bookmyshowoct24.models.SeatType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeatTypeRepository extends JpaRepository<SeatType, Long> {
    Optional<SeatType> findByValue(String value);
}
