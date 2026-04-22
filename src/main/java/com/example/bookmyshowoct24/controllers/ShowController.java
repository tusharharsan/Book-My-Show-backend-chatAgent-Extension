package com.example.bookmyshowoct24.controllers;

import com.example.bookmyshowoct24.models.ShowSeat;
import com.example.bookmyshowoct24.services.ShowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
public class ShowController {
    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    // GET /api/shows/{id}/seats
    @GetMapping("/{id}/seats")
    public List<ShowSeat> getShowSeats(@PathVariable Long id) {
        return showService.getShowSeats(id);
    }
}
