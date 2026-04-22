package com.example.bookmyshowoct24.services;

import com.example.bookmyshowoct24.models.SeatType;
import com.example.bookmyshowoct24.models.Show;
import com.example.bookmyshowoct24.models.ShowSeat;
import com.example.bookmyshowoct24.models.ShowSeatType;
import com.example.bookmyshowoct24.repositories.ShowSeatTypeRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PriceCalculatorService {
    // Weekend shows cost 25% more.
    private static final double WEEKEND_MULTIPLIER = 1.25;
    // Shows between 6pm and 10pm cost 15% more (prime time).
    private static final double PRIME_TIME_MULTIPLIER = 1.15;
    private static final int PRIME_START_HOUR = 18;
    private static final int PRIME_END_HOUR = 22;

    private final ShowSeatTypeRepository showSeatTypeRepository;

    public PriceCalculatorService(ShowSeatTypeRepository showSeatTypeRepository) {
        this.showSeatTypeRepository = showSeatTypeRepository;
    }

    /*
     * Final price = (sum of base prices by seat type) * weekend_mult * prime_time_mult.
     *
     * Example flow — user picks 3 seats (A1 GOLD, A2 GOLD, B5 SILVER) for a
     * Saturday 7:30pm show. DB has ShowSeatType rows: GOLD=500, SILVER=300.
     *
     *   1. show           = showSeats.get(0).getShow()                // the show these seats belong to
     *   2. showSeatTypes  = [ {GOLD, 500}, {SILVER, 300} ]             // one DB call
     *   3. priceBySeatType= { GOLD -> 500, SILVER -> 300 }             // Map for O(1) lookup
     *   4. base           = 500 (A1) + 500 (A2) + 300 (B5) = 1300      // one pass over seats
     *   5. multiplier     = 1.25 (Saturday) * 1.15 (7:30pm prime) = 1.4375
     *   6. final          = round(1300 * 1.4375) = round(1868.75) = 1869
     *
     * Simpler case: 2 GOLD seats at 500 each, Saturday 7pm show
     *   base   = 1000 -> * 1.25 = 1250 -> * 1.15 = 1437.5 -> rounds to 1438
     */
    public int calculatePrice(List<ShowSeat> showSeats) {
        if (showSeats.isEmpty()) {
            return 0;
        }

        Show show = showSeats.get(0).getShow();
        List<ShowSeatType> showSeatTypes = showSeatTypeRepository.findAllByShow(show);

        Map<SeatType, Integer> priceBySeatType = showSeatTypes.stream()
                .collect(Collectors.toMap(ShowSeatType::getSeatType, ShowSeatType::getPrice));

        int base = showSeats.stream()
                .mapToInt(s -> priceBySeatType.get(s.getSeat().getSeatType()))
                .sum();

        double multiplier = multiplierFor(show);
        return (int) Math.round(base * multiplier);
    }

    private double multiplierFor(Show show) {
        double multiplier = 1.0;
        LocalDateTime start = show.getStartTime().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDateTime();

        DayOfWeek dayOfWeek = start.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            multiplier *= WEEKEND_MULTIPLIER;
        }

        int hour = start.getHour();
        if (hour >= PRIME_START_HOUR && hour < PRIME_END_HOUR) {
            multiplier *= PRIME_TIME_MULTIPLIER;
        }

        return multiplier;
    }
}
