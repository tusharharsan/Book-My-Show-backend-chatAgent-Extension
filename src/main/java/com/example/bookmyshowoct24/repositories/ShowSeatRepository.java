package com.example.bookmyshowoct24.repositories;

import com.example.bookmyshowoct24.models.ShowSeat;
import com.example.bookmyshowoct24.models.ShowSeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @Override
    List<ShowSeat> findAllById(Iterable<Long> showSeatIds);

    ShowSeat save(ShowSeat showSeat);

    // One SQL UPDATE instead of fetch + save per seat.
    // Example: updateSeatStatus(BOOKED, 42) runs:
    //   UPDATE show_seat SET show_seat_status = 'BOOKED' WHERE id = 42;
    @Modifying
    @Query("update ShowSeat ss set ss.showSeatStatus = ?1 where ss.id = ?2")
    void updateSeatStatus(ShowSeatStatus status, Long id);
}
