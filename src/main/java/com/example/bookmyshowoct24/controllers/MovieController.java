package com.example.bookmyshowoct24.controllers;

import com.example.bookmyshowoct24.models.Category;
import com.example.bookmyshowoct24.models.Language;
import com.example.bookmyshowoct24.models.Movie;
import com.example.bookmyshowoct24.models.Show;
import com.example.bookmyshowoct24.services.MovieService;
import com.example.bookmyshowoct24.services.ShowService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {
    private final MovieService movieService;
    private final ShowService showService;

    public MovieController(MovieService movieService, ShowService showService) {
        this.movieService = movieService;
        this.showService = showService;
    }

    // GET /api/movies?name=Pushpa&city=Mumbai&language=HINDI&category=ACTION&minRating=7
    @GetMapping
    public List<Movie> searchMovies(@RequestParam(required = false) String name,
                                    @RequestParam(required = false) String city,
                                    @RequestParam(required = false) Language language,
                                    @RequestParam(required = false) Category category,
                                    @RequestParam(required = false) Double minRating) {
        return movieService.searchMovies(name, city, language, category, minRating);
    }

    // GET /api/movies/{id}/shows?city=Mumbai&date=2026-04-19
    @GetMapping("/{id}/shows")
    public List<Show> getShowsForMovie(@PathVariable Long id,
                                       @RequestParam(required = false) String city,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date date) {
        return showService.getShows(id, city, date);
    }
}
