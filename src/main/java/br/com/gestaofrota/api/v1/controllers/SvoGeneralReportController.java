package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.application.reports.ReportGenerationService;
import br.com.gestaofrota.application.reports.ReportValidator;
import br.com.gestaofrota.application.reports.SvoGeneralReportService;
import br.com.gestaofrota.application.reports.SvoGeneralReportService.Result;
import br.com.gestaofrota.application.reports.SvoOperationsReportService.Period;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
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
@RequestMapping("/api/v1/reports/svo/general")
@PreAuthorize("!hasRole('MOTORISTA') and hasAuthority('VEHICLES_READ') and hasAuthority('TRIPS_READ')"
        + " and hasAuthority('TRIP_REQUESTS_READ') and hasAuthority('DRIVERS_READ') and hasAuthority('SECTORS_READ')"
        + " and hasAuthority('FUELINGS_READ') and hasAuthority('MAINTENANCES_READ')")
public class SvoGeneralReportController {
    private static final Duration RETENTION = Duration.ofMinutes(30);
    private static final int MAX_SNAPSHOTS = 128;
    private final SvoGeneralReportService queries;
    private final ReportValidator validator;
    private final ReportGenerationService generator;
    private final LinkedHashMap<UUID, Snapshot> snapshots = new LinkedHashMap<>();

    public SvoGeneralReportController(SvoGeneralReportService queries, ReportValidator validator,
                                      ReportGenerationService generator) {
        this.queries = queries;
        this.validator = validator;
        this.generator = generator;
    }

    @GetMapping
    public ResponseEntity<View> view(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam(defaultValue = "today") Period period,
                                     @RequestParam(required = false) UUID sectorId) {
        var result = queries.query(period, sectorId);
        for (var section : result.sections()) {
            validator.validate(section.data(), ReportFormat.PDF);
            validator.validate(section.data(), ReportFormat.EXCEL);
        }
        var id = UUID.randomUUID();
        synchronized (snapshots) {
            snapshots.values().removeIf(Snapshot::expired);
            if (snapshots.size() >= MAX_SNAPSHOTS) snapshots.pollFirstEntry();
            snapshots.put(id, new Snapshot(user.id(), user.sectorId(), result, Instant.now().plus(RETENTION)));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new View(id, SvoGeneralReportService.TITLE, result.header().referenceAt(),
                        result.header().referenceLabel(), result.sections().stream().map(section -> {
                            var data = section.data();
                            return new SectionView(section.indicator(), data.title(), data.source(), data.columns(), data.rows());
                        }).toList()));
    }

    @GetMapping("/{format:pdf|excel}")
    public ResponseEntity<byte[]> download(@PathVariable String format, @RequestParam UUID snapshotId,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        Snapshot snapshot;
        synchronized (snapshots) {
            snapshot = snapshots.get(snapshotId);
        }
        if (snapshot == null || snapshot.expired() || !snapshot.owner.equals(user.id())
                || !Objects.equals(snapshot.sector, user.sectorId())) {
            throw new ResourceNotFoundException("Consulta de relatório", snapshotId);
        }
        boolean pdf = format.equals("pdf");
        var sections = snapshot.result.sections().stream().map(SvoGeneralReportService.Section::data).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf ? "svo-geral.pdf" : "svo-geral.xlsx").build().toString())
                .body(generator.generateSections(SvoGeneralReportService.TITLE, sections,
                        pdf ? ReportFormat.PDF : ReportFormat.EXCEL));
    }

    public record View(UUID snapshotId, String title, Instant referenceAt, String referenceLabel,
                       List<SectionView> sections) {}
    public record SectionView(String indicator, String title, String context, List<String> columns,
                              List<List<String>> rows) {}
    private record Snapshot(UUID owner, UUID sector, Result result, Instant expiresAt) {
        boolean expired() { return !Instant.now().isBefore(expiresAt); }
    }
}
