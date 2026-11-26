package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.responses.VehicleResponse;
import br.com.gestaofrota.application.services.TripService;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportHeader;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FleetStatusReportService {
    private final VehicleService vehicles;
    private final TripService trips;

    public FleetStatusReportService(VehicleService vehicles, TripService trips) {
        this.vehicles = vehicles;
        this.trips = trips;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result query() {
        var referenceAt = Instant.now();
        var openVehicles = new HashSet<UUID>();
        var pageable = PageRequest.of(0, 100,
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        while (true) {
            var page = trips.history(null, null, null, null, pageable);
            for (var trip : page.getContent()) {
                if (trip.getStatus() == TripStatus.OPEN) openVehicles.add(trip.getVehicle().getId());
            }
            if (!page.hasNext()) break;
            pageable = pageable.next();
        }
        return query(referenceAt, openVehicles);
    }

    Result query(Instant referenceAt, Set<UUID> openVehicles) {
        return classify(referenceAt, vehicles.list().stream().map(VehicleResponse::from).toList(), openVehicles);
    }

    public static ReportData toReportData(String title, Result result, ReportHeader.Kind kind) {
        return new ReportData(title, "Total da frota ativa: " + result.totalActive(),
                List.of("Status", "Veículos", "Percentual"),
                result.rows().stream().map(row -> List.of(row.label(), Integer.toString(row.count()),
                        row.percentage().toPlainString().replace('.', ',') + "%")).toList(),
                new ReportHeader(ReportHeader.Module.SVO, kind, result.referenceAt()),
                Map.of(1, ReportData.NumberFormat.INTEGER, 2, ReportData.NumberFormat.PERCENT));
    }

    static Result classify(Instant referenceAt, List<VehicleResponse> fleet, Set<UUID> openVehicles) {
        var counts = new EnumMap<Status, Integer>(Status.class);
        int total = 0;
        for (var vehicle : fleet) {
            if (!vehicle.active()) continue;
            total++;
            Status status;
            if (vehicle.inMaintenance()) status = Status.MAINTENANCE;
            else if (openVehicles.contains(vehicle.id())) status = Status.ON_ROUTE;
            else if (vehicle.available()) status = Status.AVAILABLE;
            else status = Status.UNAVAILABLE;
            counts.merge(status, 1, Integer::sum);
        }

        var rows = new java.util.ArrayList<Row>();
        for (var status : Status.values()) {
            int count = counts.getOrDefault(status, 0);
            var percentage = BigDecimal.valueOf(count * 100L)
                    .divide(BigDecimal.valueOf(Math.max(1, total)), 2, RoundingMode.HALF_UP);
            rows.add(new Row(status.label, count, percentage));
        }
        return new Result(referenceAt, total, List.copyOf(rows));
    }

    private enum Status {
        AVAILABLE("Disponíveis"), ON_ROUTE("Em rota"),
        MAINTENANCE("Em manutenção"), UNAVAILABLE("Indisponíveis");

        final String label;

        Status(String label) {
            this.label = label;
        }
    }

    public record Row(String label, int count, BigDecimal percentage) {}
    public record Result(Instant referenceAt, int totalActive, List<Row> rows) {}
}
