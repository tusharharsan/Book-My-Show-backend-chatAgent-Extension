package com.example.bookmyshowoct24.services;

import com.example.bookmyshowoct24.models.*;
import com.example.bookmyshowoct24.repositories.CityRepository;
import com.example.bookmyshowoct24.repositories.MovieRepository;
import com.example.bookmyshowoct24.repositories.ShowRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MovieService {
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final CityRepository cityRepository;

    public MovieService(MovieRepository movieRepository,
                        ShowRepository showRepository,
                        CityRepository cityRepository) {
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
        this.cityRepository = cityRepository;
    }

    /*
     * Returns movies matching any combination of filters. All filters are optional;
     * pass null to skip that filter. name does case-insensitive substring match
     * ("push" matches "Pushpa 2: The Rule"). Example:
     *   searchMovies("pushpa", "Mumbai", Language.HINDI, Category.ACTION, 7.0)
     *   → action movies named like "pushpa" in Hindi with rating >= 7.0 in Mumbai.
     */
    public List<Movie> searchMovies(String name,
                                    String cityName,
                                    Language language,
                                    Category category,
                                    Double minRating) {
        Set<Long> screenIdsInCity = cityName == null ? null : findScreenIdsInCity(cityName);
        String nameLower = name == null ? null : name.toLowerCase();

        return movieRepository.findAll().stream()
                .filter(m -> nameLower == null ||
                        (m.getName() != null && m.getName().toLowerCase().contains(nameLower)))
                .filter(m -> category == null || m.getCategory() == category)
                .filter(m -> minRating == null || m.getRating() >= minRating)
                .filter(m -> language == null ||
                        (m.getLanguages() != null && m.getLanguages().contains(language)))
                .filter(m -> screenIdsInCity == null || hasShowInScreens(m, screenIdsInCity))
                .toList();
    }

    public Movie getMovie(Long id) {
        return movieRepository.findById(id).orElse(null);
    }

    private Set<Long> findScreenIdsInCity(String cityName) {
        return cityRepository.findByName(cityName)
                .map(city -> city.getTheatres().stream()
                        .flatMap(t -> t.getScreens().stream())
                        .map(Screen::getId)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private boolean hasShowInScreens(Movie movie, Set<Long> screenIds) {
        return showRepository.findAllByMovieId(movie.getId()).stream()
                .anyMatch(s -> s.getScreen() != null && screenIds.contains(s.getScreen().getId()));
    }
}
