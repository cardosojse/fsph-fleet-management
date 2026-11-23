package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.application.reports.FleetStatusReportService;
import br.com.gestaofrota.application.reports.ReportGenerationService;
import br.com.gestaofrota.application.reports.ReportValidator;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.domain.reports.ReportHeader;
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
@PreAuthorize("hasAuthority('VEHICLES_READ') and hasAuthority('TRIPS_READ') and !hasRole('MOTORISTA')")
public class FleetStatusReportController {
    private static final Duration RETENTION = Duration.ofMinutes(30);
    private static final int MAX_SNAPSHOTS = 1024;

    private final FleetStatusReportService queries;
    private final ReportGenerationService generator;
    private final ReportValidator validator;
    private final LinkedHashMap<UUID, Snapshot> snapshots = new LinkedHashMap<>();

    public FleetStatusReportController(FleetStatusReportService queries, ReportGenerationService generator,
                                       ReportValidator validator) {
        this.queries = queries;
        this.generator = generator;
        this.validator = validator;
    }

    @GetMapping("/{kind:availability|fleet-status}")
    public ResponseEntity<View> view(@PathVariable String kind, @AuthenticationPrincipal AuthenticatedUser user) {
        var result = queries.query();
        var id = UUID.randomUUID();
        var title = kind.equals("availability") ? "Disponibilidade da frota" : "Frota por status";
        var view = new View(id, title, result.referenceAt(), header(result.referenceAt()).referenceLabel(),
                result.totalActive(), result.rows());
        synchronized (snapshots) {
            snapshots.values().removeIf(Snapshot::expired);
            if (snapshots.size() >= MAX_SNAPSHOTS) snapshots.pollFirstEntry();
            snapshots.put(id, new Snapshot(user.id(), user.sectorId(), kind, view, Instant.now().plus(RETENTION)));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view);
    }

    @GetMapping("/{kind:availability|fleet-status}/{format:pdf|excel}")
    public ResponseEntity<byte[]> download(@PathVariable String kind, @PathVariable String format,
                                            @RequestParam UUID snapshotId,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        Snapshot snapshot;
        synchronized (snapshots) {
            snapshot = snapshots.get(snapshotId);
        }
        if (snapshot == null || snapshot.expired() || !snapshot.owner().equals(user.id())
                || !Objects.equals(snapshot.sector(), user.sectorId()) || !snapshot.kind().equals(kind)) {
            throw new ResourceNotFoundException("Consulta de relatório", snapshotId);
        }
        var view = snapshot.view();
        var data = FleetStatusReportService.toReportData(view.title(),
                new FleetStatusReportService.Result(view.referenceAt(), view.totalActive(), view.rows()),
                ReportHeader.Kind.INDIVIDUAL);
        boolean pdf = format.equals("pdf");
        var reportFormat = pdf ? ReportFormat.PDF : ReportFormat.EXCEL;
        validator.validate(data, reportFormat);
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(kind + (pdf ? ".pdf" : ".xlsx")).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(generator.generate(data, reportFormat));
    }

    private ReportHeader header(Instant referenceAt) {
        return new ReportHeader(ReportHeader.Module.SVO, ReportHeader.Kind.INDIVIDUAL, referenceAt);
    }

    public record View(UUID snapshotId, String title, Instant referenceAt, String referenceLabel, int totalActive,
                       List<FleetStatusReportService.Row> rows) {}

    private record Snapshot(UUID owner, UUID sector, String kind, View view, Instant expiresAt) {
        boolean expired() {
            return !Instant.now().isBefore(expiresAt);
        }
    }
}
