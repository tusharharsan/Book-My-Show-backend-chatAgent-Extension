package com.example.bookmyshowoct24.repositories;

import com.example.bookmyshowoct24.models.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findAllByMovieId(Long movieId);
}
