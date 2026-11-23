package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.FuelingReportFilterRequest;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportResponse;
import br.com.gestaofrota.application.reports.*;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/fuelings")
@PreAuthorize("hasAuthority('FUELINGS_READ') and hasAnyRole('ADMIN', 'GESTOR_FROTA')")
public class FuelingReportController {
    private final FuelingReportQueryService queries;
    private final FuelingReportMapper mapper;
    private final ReportGenerationService generator;

    public FuelingReportController(FuelingReportQueryService queries, FuelingReportMapper mapper,
                                  ReportGenerationService generator) {
        this.queries = queries;
        this.mapper = mapper;
        this.generator = generator;
    }

    @GetMapping
    public FuelingReportResponse view(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID vehicleId) {
        return queries.query(user, new FuelingReportFilterRequest(startDate, endDate, vehicleId));
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID vehicleId) {
        return download(view(user, startDate, endDate, vehicleId), ReportFormat.PDF);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID vehicleId) {
        return download(view(user, startDate, endDate, vehicleId), ReportFormat.EXCEL);
    }

    private ResponseEntity<byte[]> download(FuelingReportResponse report, ReportFormat format) {
        boolean pdf = format == ReportFormat.PDF;
        return ResponseEntity.ok()
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("abastecimentos." + (pdf ? "pdf" : "xlsx")).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(generator.generate(mapper.toReportData(report, format), format));
    }
}
