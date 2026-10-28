package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.infra.repositories.TripRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class TripUtilizationServiceTests {
    @Mock private TripRepository trips;
    @Mock private VehicleRepository vehicles;

    private TripUtilizationService service;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        service = new TripUtilizationService(trips, vehicles, "UTC");
        vehicleId = UUID.randomUUID();
        lenient().when(vehicles.existsById(vehicleId)).thenReturn(true);
    }

    @Test
    void calculatesPercentageFromDistinctDays() {
        givenIntervals(interval("2026-01-01T10:00:00Z", "2026-01-01T11:00:00Z"),
                interval("2026-01-03T10:00:00Z", "2026-01-03T11:00:00Z"));

        assertEquals("50.00", service.calculateRate(vehicleId, date("2026-01-01"), date("2026-01-04")).toPlainString());
    }

    @Test
    void countsRepeatedTripsOnOneDayOnlyOnce() {
        givenIntervals(interval("2026-01-02T08:00:00Z", "2026-01-02T09:00:00Z"),
                interval("2026-01-02T15:00:00Z", "2026-01-02T16:00:00Z"));

        assertEquals("25.00", service.calculateRate(vehicleId, date("2026-01-01"), date("2026-01-04")).toPlainString());
    }

    @Test
    void countsEveryCalendarDayOfTripCrossingMidnight() {
        givenIntervals(interval("2026-01-01T23:30:00Z", "2026-01-02T00:30:00Z"));

        assertEquals("66.67", service.calculateRate(vehicleId, date("2026-01-01"), date("2026-01-03")).toPlainString());
    }

    @Test
    void clipsTripsToInclusivePeriodBoundariesAndDoesNotCountEndAtMidnightAsNextDay() {
        givenIntervals(interval("2026-01-01T22:00:00Z", "2026-01-02T00:00:00Z"),
                interval("2026-01-03T23:00:00Z", "2026-01-04T00:00:00Z"));

        assertEquals("50.00", service.calculateRate(vehicleId, date("2026-01-02"), date("2026-01-03")).toPlainString());
    }

    @Test
    void returnsZeroWhenThereAreNoCompletedTripsInPeriod() {
        givenIntervals();

        assertEquals("0.00", service.calculateRate(vehicleId, date("2026-01-01"), date("2026-01-04")).toPlainString());
    }

    @Test
    void rejectsReversedDatePeriod() {
        assertThrows(InvalidRequestException.class,
                () -> service.calculateRate(vehicleId, date("2026-01-02"), date("2026-01-01")));
    }

    private void givenIntervals(TripRepository.CompletedTripInterval... intervals) {
        when(trips.findCompletedIntervalsOverlappingPeriod(eq(vehicleId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(intervals));
    }

    private TripRepository.CompletedTripInterval interval(String start, String end) {
        Instant startedAt = Instant.parse(start);
        Instant endedAt = Instant.parse(end);
        return new TripRepository.CompletedTripInterval() {
            @Override public Instant getStartedAt() { return startedAt; }
            @Override public Instant getEndedAt() { return endedAt; }
        };
    }

    private LocalDate date(String value) { return LocalDate.parse(value); }
}
