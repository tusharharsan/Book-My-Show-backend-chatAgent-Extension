package com.example.bookmyshowoct24.services;

import com.example.bookmyshowoct24.exceptions.ShowNotFoundException;
import com.example.bookmyshowoct24.models.Screen;
import com.example.bookmyshowoct24.models.Show;
import com.example.bookmyshowoct24.models.ShowSeat;
import com.example.bookmyshowoct24.repositories.CityRepository;
import com.example.bookmyshowoct24.repositories.ShowRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShowService {
    private final ShowRepository showRepository;
    private final CityRepository cityRepository;

    public ShowService(ShowRepository showRepository, CityRepository cityRepository) {
        this.showRepository = showRepository;
        this.cityRepository = cityRepository;
    }

    /*
     * Returns shows for a movie. city and date are optional filters.
     * date is compared as a calendar day (ignores hours/minutes).
     */
    public List<Show> getShows(Long movieId, String cityName, Date date) {
        Set<Long> screenIdsInCity = cityName == null ? null : findScreenIdsInCity(cityName);
        LocalDate targetDate = date == null ? null : toLocalDate(date);

        return showRepository.findAllByMovieId(movieId).stream()
                .filter(s -> screenIdsInCity == null ||
                        (s.getScreen() != null && screenIdsInCity.contains(s.getScreen().getId())))
                .filter(s -> targetDate == null || toLocalDate(s.getStartTime()).equals(targetDate))
                .toList();
    }

    public List<ShowSeat> getShowSeats(Long showId) {
        return showRepository.findById(showId)
                .map(Show::getShowSeats)
                .orElseThrow(() -> new ShowNotFoundException("Show not found for id: " + showId));
    }

    private Set<Long> findScreenIdsInCity(String cityName) {
        return cityRepository.findByName(cityName)
                .map(city -> city.getTheatres().stream()
                        .flatMap(t -> t.getScreens().stream())
                        .map(Screen::getId)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
