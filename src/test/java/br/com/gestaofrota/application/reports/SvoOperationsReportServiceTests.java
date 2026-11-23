package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.application.reports.SvoOperationsReportService.Period;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.Trip;
import br.com.gestaofrota.domain.models.TripRequest;
import br.com.gestaofrota.domain.models.Vehicle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SvoOperationsReportServiceTests {
    @Test
    void groupsRequestsAtSaoPauloMidnight() {
        assertEquals(Map.of(LocalDate.of(2026, 11, 18), 1L, LocalDate.of(2026, 11, 19), 2L),
                SvoOperationsReportService.requestsByDay(List.of(
                        request("2026-11-19T02:59:59Z"), request("2026-11-19T03:00:00Z"),
                        request("2026-11-20T02:59:59Z"))));
    }

    @Test
    void filtersCurrentVehicleSectorAndDaysIncludingWeekBoundaries() {
        var sectorId = UUID.randomUUID();
        var sector = mock(Sector.class);
        when(sector.getId()).thenReturn(sectorId);
        var vehicle = mock(Vehicle.class);
        when(vehicle.getSector()).thenReturn(sector);
        var trip = mock(Trip.class);
        when(trip.getVehicle()).thenReturn(vehicle);
        var reference = Instant.parse("2026-11-19T03:00:00Z");
        when(trip.getStartedAt()).thenReturn(Instant.parse("2026-11-13T03:00:00Z"));
        assertTrue(SvoOperationsReportService.matches(trip, sectorId, Period.week, reference));
        assertFalse(SvoOperationsReportService.matches(trip, sectorId, Period.today, reference));
        assertFalse(SvoOperationsReportService.matches(trip, UUID.randomUUID(), Period.all, reference));
        when(trip.getStartedAt()).thenReturn(Instant.parse("2026-11-13T02:59:59Z"));
        assertFalse(SvoOperationsReportService.matches(trip, sectorId, Period.week, reference));
        assertTrue(SvoOperationsReportService.matches(trip, null, Period.all, reference));
        when(trip.getStartedAt()).thenReturn(Instant.parse("2026-11-19T03:00:00Z"));
        assertTrue(SvoOperationsReportService.matches(trip, sectorId, Period.today, reference));
    }

    private TripRequest request(String created) {
        var request = mock(TripRequest.class);
        when(request.getCreatedAt()).thenReturn(Instant.parse(created));
        return request;
    }
}
