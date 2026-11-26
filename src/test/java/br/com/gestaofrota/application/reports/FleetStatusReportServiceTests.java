package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.responses.VehicleResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FleetStatusReportServiceTests {
    @Test
    void classifiesActiveVehiclesExactlyOnceWithMaintenanceFirst() {
        var available = vehicle(true, false, true);
        var open = vehicle(true, false, true);
        var maintenance = vehicle(true, true, true);
        var unavailable = vehicle(true, false, false);
        var inactive = vehicle(false, false, true);
        var now = Instant.parse("2026-11-19T12:00:00Z");
        var result = FleetStatusReportService.classify(now,
                List.of(available, open, maintenance, unavailable, inactive),
                Set.of(open.id(), maintenance.id(), inactive.id(), UUID.randomUUID()));

        assertEquals(now, result.referenceAt());
        assertEquals(4, result.totalActive());
        assertEquals(List.of(1, 1, 1, 1), result.rows().stream().map(FleetStatusReportService.Row::count).toList());
        result.rows().forEach(row -> assertEquals(new BigDecimal("25.00"), row.percentage()));
    }

    @Test
    void emptyFleetHasFourZeroRows() {
        var result = FleetStatusReportService.classify(Instant.EPOCH, List.of(), Set.of());
        assertEquals(0, result.totalActive());
        assertEquals(4, result.rows().size());
        result.rows().forEach(row -> {
            assertEquals(0, row.count());
            assertEquals(new BigDecimal("0.00"), row.percentage());
        });
    }

    private VehicleResponse vehicle(boolean active, boolean maintenance, boolean available) {
        return new VehicleResponse(UUID.randomUUID(), null, "SVO0001", "Teste", 4, "Teste", 0,
                active, maintenance, available, Instant.EPOCH, Instant.EPOCH);
    }
}
