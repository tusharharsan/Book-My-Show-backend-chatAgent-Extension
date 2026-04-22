package com.example.bookmyshowoct24.repositories;

import com.example.bookmyshowoct24.models.Screen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScreenRepository extends JpaRepository<Screen, Long> {
}
