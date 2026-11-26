package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.application.reports.ReportGenerationService;
import br.com.gestaofrota.application.reports.ReportValidator;
import br.com.gestaofrota.application.reports.SvoOperationsReportService;
import br.com.gestaofrota.application.reports.SvoFleetPanelsReportService;
import br.com.gestaofrota.application.reports.SvoOperationsReportService.Period;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/svo")
public class SvoOperationsReportController {
    private static final String REQUESTS = "hasAuthority('TRIP_REQUESTS_READ')";
    private static final String TRIPS = "hasAuthority('TRIPS_READ') and !hasRole('MOTORISTA')";
    private static final String TRIAGE = REQUESTS + " and " + TRIPS;
    private static final String ONGOING = TRIPS + " and hasAuthority('VEHICLES_READ')"
            + " and hasAuthority('DRIVERS_READ') and hasAuthority('SECTORS_READ')";
    private static final String FUELINGS = "hasAuthority('FUELINGS_READ')";
    private static final String MAINTENANCE = "hasAuthority('MAINTENANCES_READ')";
    private static final Duration RETENTION = Duration.ofMinutes(30);
    private static final int MAX_SNAPSHOTS = 256;

    private final SvoOperationsReportService queries;
    private final SvoFleetPanelsReportService fleetPanels;
    private final ReportGenerationService generator;
    private final ReportValidator validator;
    private final LinkedHashMap<UUID, Snapshot> snapshots = new LinkedHashMap<>();

    public SvoOperationsReportController(SvoOperationsReportService queries, SvoFleetPanelsReportService fleetPanels,
                                         ReportGenerationService generator,
                                         ReportValidator validator) {
        this.queries = queries;
        this.fleetPanels = fleetPanels;
        this.generator = generator;
        this.validator = validator;
    }

    @GetMapping("/triage")
    @PreAuthorize(TRIAGE)
    public ResponseEntity<View> triage(@AuthenticationPrincipal AuthenticatedUser user) {
        return store("triage", user, queries.triage());
    }

    @GetMapping("/daily-requests")
    @PreAuthorize(REQUESTS)
    public ResponseEntity<View> daily(@AuthenticationPrincipal AuthenticatedUser user) {
        return store("daily-requests", user, queries.daily());
    }

    @GetMapping("/ongoing-trips")
    @PreAuthorize(ONGOING)
    public ResponseEntity<View> ongoing(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam(defaultValue = "today") Period period,
                                        @RequestParam(required = false) UUID sectorId) {
        return store("ongoing-trips", user, queries.ongoing(period, sectorId));
    }

    @GetMapping("/vehicle-consumption")
    @PreAuthorize(FUELINGS)
    public ResponseEntity<View> consumption(@AuthenticationPrincipal AuthenticatedUser user) {
        return store("vehicle-consumption", user, fleetPanels.consumption());
    }

    @GetMapping("/recent-fuelings")
    @PreAuthorize(FUELINGS)
    public ResponseEntity<View> recentFuelings(@AuthenticationPrincipal AuthenticatedUser user) {
        return store("recent-fuelings", user, fleetPanels.recentFuelings());
    }

    @GetMapping("/maintenance-alerts")
    @PreAuthorize(MAINTENANCE)
    public ResponseEntity<View> maintenanceAlerts(@AuthenticationPrincipal AuthenticatedUser user) {
        return store("maintenance-alerts", user, fleetPanels.maintenanceAlerts());
    }

    @GetMapping("/{kind:triage|daily-requests|ongoing-trips|vehicle-consumption|recent-fuelings|maintenance-alerts}/{format:pdf|excel}")
    @PreAuthorize("(#kind == 'triage' and (" + TRIAGE + ")) or (#kind == 'daily-requests' and (" + REQUESTS
            + ")) or (#kind == 'ongoing-trips' and (" + ONGOING + "))"
            + " or ((#kind == 'vehicle-consumption' or #kind == 'recent-fuelings') and (" + FUELINGS + "))"
            + " or (#kind == 'maintenance-alerts' and (" + MAINTENANCE + "))")
    public ResponseEntity<byte[]> download(@PathVariable String kind, @PathVariable String format,
                                            @RequestParam UUID snapshotId,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        Snapshot snapshot;
        synchronized (snapshots) {
            snapshot = snapshots.get(snapshotId);
        }
        if (snapshot == null || snapshot.expired() || !snapshot.owner.equals(user.id())
                || !Objects.equals(snapshot.sector, user.sectorId()) || !snapshot.kind.equals(kind)) {
            throw new ResourceNotFoundException("Consulta de relatório", snapshotId);
        }
        boolean pdf = format.equals("pdf");
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(kind + (pdf ? ".pdf" : ".xlsx")).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(generator.generate(snapshot.data, pdf ? ReportFormat.PDF : ReportFormat.EXCEL));
    }

    private ResponseEntity<View> store(String kind, AuthenticatedUser user, ReportData data) {
        validator.validate(data, ReportFormat.PDF);
        validator.validate(data, ReportFormat.EXCEL);
        var id = UUID.randomUUID();
        synchronized (snapshots) {
            snapshots.values().removeIf(Snapshot::expired);
            if (snapshots.size() >= MAX_SNAPSHOTS) snapshots.pollFirstEntry();
            snapshots.put(id, new Snapshot(user.id(), user.sectorId(), kind, data, Instant.now().plus(RETENTION)));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new View(id, data.title(),
                data.header().referenceAt(), data.header().referenceLabel(), data.source(), data.columns(), data.rows()));
    }

    public record View(UUID snapshotId, String title, Instant referenceAt, String referenceLabel, String context,
                       List<String> columns, List<List<String>> rows) {}

    private record Snapshot(UUID owner, UUID sector, String kind, ReportData data, Instant expiresAt) {
        boolean expired() { return !Instant.now().isBefore(expiresAt); }
    }
}
