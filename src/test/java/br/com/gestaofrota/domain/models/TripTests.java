package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TripTests {

    @Mock private Vehicle vehicle;
    @Mock private Driver driver;

    @Test
    void startsAsOpenWithRequiredVehicleDriverAndInitialReading() {
        Instant startedAt = Instant.parse("2026-01-10T09:00:00Z");

        Trip trip = new Trip(vehicle, driver, startedAt, 1200);

        assertAll(
                () -> assertSame(vehicle, trip.getVehicle()),
                () -> assertSame(driver, trip.getDriver()),
                () -> assertEquals(startedAt, trip.getStartedAt()),
                () -> assertEquals(1200, trip.getInitialOdometerKm()),
                () -> assertNull(trip.getEndedAt()),
                () -> assertNull(trip.getFinalOdometerKm()),
                () -> assertEquals(TripStatus.OPEN, trip.getStatus())
        );
        verifyNoInteractions(vehicle, driver);
    }

    @Test
    void allowsZeroInitialOdometer() {
        Trip trip = new Trip(vehicle, driver, Instant.parse("2026-01-10T09:00:00Z"), 0);

        assertEquals(0, trip.getInitialOdometerKm());
        assertEquals(TripStatus.OPEN, trip.getStatus());
    }

    @Test
    void rejectsNegativeInitialOdometer() {
        assertThrows(InvalidRequestException.class,
                () -> new Trip(vehicle, driver, Instant.parse("2026-01-10T09:00:00Z"), -1));
    }

    @Test
    void completesTripWhenFinalReadingAndInstantAreValid() {
        Instant startedAt = Instant.parse("2026-01-10T09:00:00Z");
        Instant endedAt = Instant.parse("2026-01-10T10:00:00Z");
        Trip trip = new Trip(vehicle, driver, startedAt, 1200);

        trip.complete(endedAt, 1275);

        assertEquals(endedAt, trip.getEndedAt());
        assertEquals(1275L, trip.getFinalOdometerKm());
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
    }

    @Test
    void allowsSameFinalAndInitialOdometerAndSameInstant() {
        Instant instant = Instant.parse("2026-01-10T09:00:00Z");
        Trip trip = new Trip(vehicle, driver, instant, 1200);

        trip.complete(instant, 1200);

        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertEquals(1200L, trip.getFinalOdometerKm());
    }

    @Test
    void rejectsNegativeFinalOdometerWithoutChangingOpenTrip() {
        Trip trip = new Trip(vehicle, driver, Instant.parse("2026-01-10T09:00:00Z"), 1200);

        assertThrows(InvalidRequestException.class,
                () -> trip.complete(Instant.parse("2026-01-10T10:00:00Z"), -1));

        assertEquals(TripStatus.OPEN, trip.getStatus());
        assertNull(trip.getEndedAt());
    }

    @Test
    void rejectsFinalOdometerBelowInitialWithoutChangingOpenTrip() {
        Trip trip = new Trip(vehicle, driver, Instant.parse("2026-01-10T09:00:00Z"), 1200);

        assertThrows(InvalidRequestException.class,
                () -> trip.complete(Instant.parse("2026-01-10T10:00:00Z"), 1199));

        assertEquals(TripStatus.OPEN, trip.getStatus());
        assertNull(trip.getFinalOdometerKm());
    }

    @Test
    void rejectsEndInstantBeforeStartWithoutChangingOpenTrip() {
        Trip trip = new Trip(vehicle, driver, Instant.parse("2026-01-10T09:00:00Z"), 1200);

        assertThrows(InvalidRequestException.class,
                () -> trip.complete(Instant.parse("2026-01-10T08:59:59Z"), 1201));

        assertEquals(TripStatus.OPEN, trip.getStatus());
        assertNull(trip.getEndedAt());
    }

    @Test
    void rejectsDuplicateCompletionWithoutChangingFirstCompletion() {
        Instant firstEnd = Instant.parse("2026-01-10T10:00:00Z");
        Trip trip = new Trip(vehicle, driver, Instant.parse("2026-01-10T09:00:00Z"), 1200);
        trip.complete(firstEnd, 1275);

        assertThrows(ConflictException.class,
                () -> trip.complete(Instant.parse("2026-01-10T11:00:00Z"), 1300));

        assertEquals(firstEnd, trip.getEndedAt());
        assertEquals(1275L, trip.getFinalOdometerKm());
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
    }
}
