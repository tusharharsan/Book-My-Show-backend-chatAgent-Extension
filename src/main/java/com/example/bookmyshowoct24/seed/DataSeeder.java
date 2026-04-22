package com.example.bookmyshowoct24.seed;

import com.example.bookmyshowoct24.models.*;
import com.example.bookmyshowoct24.repositories.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*
 * Loads a tiny, realistic dataset on app startup so the AI agent has something to search.
 *
 * Structure created:
 *   2 cities (Mumbai, Delhi)
 *     -> 2 theatres each
 *        -> 1 screen each (with IMAX feature on one per city)
 *           -> 12 seats (4 GOLD @ 500, 4 DIAMOND @ 800, 4 PLATINUM @ 1000)
 *   3 movies (Pushpa 2, Fighter, Dunki)
 *   ~6 shows spread across today + next 2 days
 *   1 test user  (id=1, email=test@bms.com, password=password)
 *   1 coupon     (FIRST50 — 10% off, max ₹100 discount)
 *
 * Idempotent: if any city already exists, the seeder exits without touching the DB.
 */
@Component
public class DataSeeder implements CommandLineRunner {
    private final CityRepository cityRepository;
    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final ShowSeatTypeRepository showSeatTypeRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataSeeder(CityRepository cityRepository,
                      TheatreRepository theatreRepository,
                      ScreenRepository screenRepository,
                      SeatRepository seatRepository,
                      SeatTypeRepository seatTypeRepository,
                      MovieRepository movieRepository,
                      ShowRepository showRepository,
                      ShowSeatRepository showSeatRepository,
                      ShowSeatTypeRepository showSeatTypeRepository,
                      UserRepository userRepository,
                      CouponRepository couponRepository,
                      BCryptPasswordEncoder passwordEncoder) {
        this.cityRepository = cityRepository;
        this.theatreRepository = theatreRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.movieRepository = movieRepository;
        this.showRepository = showRepository;
        this.showSeatRepository = showSeatRepository;
        this.showSeatTypeRepository = showSeatTypeRepository;
        this.userRepository = userRepository;
        this.couponRepository = couponRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (cityRepository.count() > 0) {
            return;
        }

        SeatType gold = seatType("GOLD");
        SeatType diamond = seatType("DIAMOND");
        SeatType platinum = seatType("PLATINUM");

        List<Movie> movies = List.of(
                movie("Pushpa 2: The Rule", 8.2, 180, Category.ACTION,
                        List.of(Language.HINDI, Language.TELUGU)),
                movie("Fighter", 7.8, 160, Category.ACTION, List.of(Language.HINDI)),
                movie("Dunki", 7.5, 150, Category.DRAMA, List.of(Language.HINDI))
        );

        City mumbai = buildCity("Mumbai",
                List.of("PVR Phoenix Mall", "INOX Nariman Point"),
                gold, diamond, platinum, movies, true);
        City delhi = buildCity("Delhi",
                List.of("PVR Select City", "INOX R-City"),
                gold, diamond, platinum, movies, false);

        cityRepository.save(mumbai);
        cityRepository.save(delhi);

        User testUser = new User();
        testUser.setName("Test User");
        testUser.setEmail("test@bms.com");
        testUser.setPassword(passwordEncoder.encode("password"));
        userRepository.save(testUser);

        Coupon coupon = new Coupon();
        coupon.setCode("FIRST50");
        coupon.setDiscountPercent(10);
        coupon.setMaxDiscount(100);
        coupon.setActive(true);
        couponRepository.save(coupon);
    }

    private SeatType seatType(String value) {
        SeatType st = new SeatType();
        st.setValue(value);
        return seatTypeRepository.save(st);
    }

    private Movie movie(String name, double rating, int durationMinutes,
                        Category category, List<Language> languages) {
        Movie m = new Movie();
        m.setName(name);
        m.setRating(rating);
        m.setDurationMinutes(durationMinutes);
        m.setCategory(category);
        m.setLanguages(languages);
        return movieRepository.save(m);
    }

    // Builds a City with theatres + screens + seats, AND creates shows for the given movies.
    private City buildCity(String cityName, List<String> theatreNames,
                           SeatType gold, SeatType diamond, SeatType platinum,
                           List<Movie> movies, boolean firstTheatreIsImax) {
        City city = new City();
        city.setName(cityName);

        List<Theatre> theatres = new ArrayList<>();
        for (int i = 0; i < theatreNames.size(); i++) {
            String theatreName = theatreNames.get(i);
            boolean isImax = firstTheatreIsImax && i == 0;
            theatres.add(buildTheatre(theatreName, cityName, isImax, gold, diamond, platinum, movies));
        }
        city.setTheatres(theatres);
        return city;
    }

    private Theatre buildTheatre(String name, String cityName, boolean isImax,
                                 SeatType gold, SeatType diamond, SeatType platinum,
                                 List<Movie> movies) {
        Theatre theatre = new Theatre();
        theatre.setName(name);
        theatre.setAddress(name + ", " + cityName);

        Screen screen = new Screen();
        screen.setName("Screen 1");
        screen.setFeatures(isImax ? List.of(Feature.IMAX, Feature.DOLBY) : List.of(Feature.TWO_D));
        screen.setSeats(buildSeats(gold, diamond, platinum));
        screen.setTheatre(theatre);
        screen = screenRepository.save(screen);

        theatre.setScreens(List.of(screen));
        theatre = theatreRepository.save(theatre);

        // Create shows on the saved screen — must go after the screen is saved,
        // otherwise JPA doesn't have an ID to point the Show at.
        createShowsForScreen(screen, movies, isImax);

        return theatre;
    }

    private List<Seat> buildSeats(SeatType gold, SeatType diamond, SeatType platinum) {
        List<Seat> seats = new ArrayList<>();
        seats.addAll(seatRow("A", gold, 4, 1));
        seats.addAll(seatRow("B", diamond, 4, 2));
        seats.addAll(seatRow("C", platinum, 4, 3));
        return seats;
    }

    private List<Seat> seatRow(String rowLabel, SeatType type, int count, int rowIndex) {
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Seat seat = new Seat();
            seat.setSeatNumber(rowLabel + i);
            seat.setRowValue(rowIndex);
            seat.setColValue(i);
            seat.setSeatType(type);
            seats.add(seatRepository.save(seat));
        }
        return seats;
    }

    private void createShowsForScreen(Screen screen, List<Movie> movies, boolean isImax) {
        LocalDate today = LocalDate.now();
        // 3 showtimes per day: matinee, evening, night
        List<LocalTime> slots = List.of(LocalTime.of(14, 0), LocalTime.of(19, 0), LocalTime.of(22, 0));

        // Stagger movies across days: today -> movie[0], tomorrow -> movie[1], day after -> movie[2]
        for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
            Movie movie = movies.get(dayOffset % movies.size());
            LocalTime slot = slots.get(dayOffset % slots.size());
            createShow(screen, movie, today.plusDays(dayOffset), slot, isImax);
        }
    }

    private void createShow(Screen screen, Movie movie, LocalDate date, LocalTime startTime, boolean isImax) {
        LocalDateTime start = LocalDateTime.of(date, startTime);
        Date startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
        Date endDate = Date.from(start.plusMinutes(movie.getDurationMinutes())
                .atZone(ZoneId.systemDefault()).toInstant());

        Show show = new Show();
        show.setMovie(movie);
        show.setScreen(screen);
        show.setStartTime(startDate);
        show.setEndTime(endDate);
        show.setLanguage(movie.getLanguages().get(0));
        show.setFeatures(isImax ? List.of(Feature.IMAX) : List.of(Feature.TWO_D));
        show = showRepository.save(show);

        // One ShowSeat per seat, all AVAILABLE
        for (Seat seat : screen.getSeats()) {
            ShowSeat showSeat = new ShowSeat();
            showSeat.setShow(show);
            showSeat.setSeat(seat);
            showSeat.setShowSeatStatus(ShowSeatStatus.AVAILABLE);
            showSeatRepository.save(showSeat);
        }

        // Pricing by seat type for this show — GOLD 500, DIAMOND 800, PLATINUM 1000
        savePrice(show, "GOLD", 500);
        savePrice(show, "DIAMOND", 800);
        savePrice(show, "PLATINUM", 1000);
    }

    private void savePrice(Show show, String seatTypeValue, int price) {
        SeatType seatType = seatTypeRepository.findByValue(seatTypeValue)
                .orElseThrow(() -> new IllegalStateException("SeatType not found: " + seatTypeValue));
        ShowSeatType sst = new ShowSeatType();
        sst.setShow(show);
        sst.setSeatType(seatType);
        sst.setPrice(price);
        showSeatTypeRepository.save(sst);
    }
}
