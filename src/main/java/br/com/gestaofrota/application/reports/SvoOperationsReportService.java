package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.application.services.SectorService;
import br.com.gestaofrota.application.services.TripRequestService;
import br.com.gestaofrota.application.services.TripService;
import br.com.gestaofrota.domain.enums.TripRequestStatus;
import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.models.Trip;
import br.com.gestaofrota.domain.models.TripRequest;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportHeader;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SvoOperationsReportService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withZone(ZONE);
    private final TripRequestService requests;
    private final TripService trips;
    private final SectorService sectors;
    private final ReportLimits limits;

    public SvoOperationsReportService(TripRequestService requests, TripService trips, SectorService sectors,
                                      ReportLimits limits) {
        this.requests = requests;
        this.trips = trips;
        this.sectors = sectors;
        this.limits = limits;
    }

    public ReportData triage() {
        return triage(Instant.now(), readRequests(), openTrips().count());
    }

    List<TripRequest> readRequests() { return requests.listAll(null); }

    ReportData triage(Instant reference, List<TripRequest> all, long openCount) {
        var today = day(reference);
        return new ReportData("Triagem e alocação", "Situação atual", List.of("Indicador", "Quantidade"),
                List.of(
                        count("Triagens pendentes", all.stream().filter(r -> r.getStatus() == TripRequestStatus.PENDING).count()),
                        count("Aloc. pendentes", all.stream().filter(r -> r.getStatus() == TripRequestStatus.APPROVED).count()),
                        count("Solicitações hoje", requestsByDay(all).getOrDefault(today, 0L)),
                        count("Viagens abertas", openCount)),
                header(reference), Map.of(1, ReportData.NumberFormat.INTEGER));
    }

    public ReportData daily() {
        return daily(Instant.now(), readRequests());
    }

    ReportData daily(Instant reference, List<TripRequest> all) {
        var counts = requestsByDay(all);
        var rows = new ArrayList<List<String>>();
        for (int offset = 6; offset >= 0; offset--) {
            // The dashboard steps back in 24-hour intervals, then formats each date in São Paulo.
            var date = day(reference.minus(offset, ChronoUnit.DAYS));
            rows.add(count(DATE.format(date), counts.getOrDefault(date, 0L)));
        }
        return new ReportData("Solicitações por dia", "Período: Últimos 7 dias (" + rows.getFirst().getFirst()
                + " a " + rows.getLast().getFirst() + ")", List.of("Data", "Solicitações"), rows,
                header(reference), Map.of(1, ReportData.NumberFormat.INTEGER));
    }

    public ReportData ongoing(Period period, UUID sectorId) {
        return ongoing(Instant.now(), period, sectorId, openTrips());
    }

    ReportData ongoing(Instant reference, Period period, UUID sectorId, Stream<Trip> open) {
        var sector = sectorId == null ? "Todos os setores" : sectors.find(sectorId).getName();
        var rows = open.filter(trip -> matches(trip, sectorId, period, reference))
                .limit((long) limits.maxRows() + 1)
                .map(this::tripRow).toList();
        return new ReportData("Viagens em andamento", "Período: " + period.label + " | Setor: " + sector,
                List.of("Viagem", "Setor do veículo", "Motorista", "Veículo", "Início", "Status"), rows,
                header(reference), Map.of());
    }

    Stream<Trip> openTrips() {
        var first = PageRequest.of(0, 100, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        return Stream.iterate(trips.history(null, null, null, null, first), page -> page != null,
                        page -> page.hasNext() ? trips.history(null, null, null, null, page.nextPageable()) : null)
                .flatMap(page -> page.getContent().stream()).filter(trip -> trip.getStatus() == TripStatus.OPEN);
    }

    static Map<LocalDate, Long> requestsByDay(List<TripRequest> requests) {
        var counts = new HashMap<LocalDate, Long>();
        for (var request : requests) {
            if (request.getCreatedAt() != null) counts.merge(day(request.getCreatedAt()), 1L, Long::sum);
        }
        return counts;
    }

    static boolean matches(Trip trip, UUID sectorId, Period period, Instant reference) {
        var sector = trip.getVehicle().getSector();
        if (sectorId != null && (sector == null || !sectorId.equals(sector.getId()))) return false;
        if (period == Period.all) return true;
        if (trip.getStartedAt() == null) return false;
        var start = day(trip.getStartedAt());
        var today = day(reference);
        return period == Period.today ? start.equals(today)
                : !start.isBefore(day(reference.minus(6, ChronoUnit.DAYS))) && !start.isAfter(today);
    }

    private List<String> tripRow(Trip trip) {
        var vehicle = trip.getVehicle();
        var sector = vehicle.getSector();
        return List.of(trip.getId().toString().substring(0, 8), sector == null ? "—" : sector.getName(),
                trip.getDriver().getUser().getName(), vehicle.getModel() + " • " + vehicle.getPlate(),
                trip.getStartedAt() == null ? "—" : START.format(trip.getStartedAt()), "Em execução");
    }

    private static LocalDate day(Instant instant) { return instant.atZone(ZONE).toLocalDate(); }

    private static List<String> count(String label, long value) { return List.of(label, Long.toString(value)); }

    private static ReportHeader header(Instant reference) {
        return new ReportHeader(ReportHeader.Module.SVO, ReportHeader.Kind.INDIVIDUAL, reference);
    }

    public enum Period {
        today("Hoje"), week("Últimos 7 dias"), all("Todo o período");
        private final String label;
        Period(String label) { this.label = label; }
    }
}
