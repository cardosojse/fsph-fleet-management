package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.CreateSvoDutyShiftRequest;
import br.com.gestaofrota.api.v1.dtos.requests.UpdateSvoDutyShiftRequest;
import br.com.gestaofrota.api.v1.dtos.responses.SvoCoverageReportResponse;
import br.com.gestaofrota.api.v1.dtos.responses.SvoDutyShiftResponse;
import br.com.gestaofrota.application.services.SvoDutyShiftService;
import br.com.gestaofrota.domain.enums.SvoShiftStatus;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/svo/shifts")
public class SvoDutyShiftController {

    private final SvoDutyShiftService shiftService;

    public SvoDutyShiftController(SvoDutyShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SVO_SHIFTS_READ')")
    public List<SvoDutyShiftResponse> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) SvoShiftStatus status
    ) {
        return shiftService.list(from, to, teamId, status).stream()
                .map(SvoDutyShiftResponse::from)
                .toList();
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('SVO_SHIFTS_READ')")
    public List<SvoDutyShiftResponse> currentShifts(
            @RequestParam(required = false) Instant at
    ) {
        return shiftService.getCurrentShifts(at).stream()
                .map(SvoDutyShiftResponse::from)
                .toList();
    }

    @GetMapping("/coverage-gaps")
    @PreAuthorize("hasAuthority('SVO_SHIFTS_READ')")
    public SvoCoverageReportResponse findCoverageGaps(
            @RequestParam Instant from,
            @RequestParam Instant to
    ) {
        return shiftService.findCoverageGaps(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SVO_SHIFTS_READ')")
    public SvoDutyShiftResponse find(@PathVariable UUID id) {
        return SvoDutyShiftResponse.from(shiftService.find(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SVO_SHIFTS_WRITE')")
    public ResponseEntity<SvoDutyShiftResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateSvoDutyShiftRequest request
    ) {
        SvoDutyShiftResponse result = SvoDutyShiftResponse.from(
                shiftService.create(
                        request.teamId(),
                        request.vehicleId(),
                        request.startAt(),
                        request.endAt(),
                        request.shiftType(),
                        request.notes(),
                        user
                )
        );
        return ResponseEntity.created(URI.create("/api/v1/svo/shifts/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SVO_SHIFTS_WRITE')")
    public SvoDutyShiftResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSvoDutyShiftRequest request
    ) {
        return SvoDutyShiftResponse.from(
                shiftService.update(
                        id,
                        request.teamId(),
                        request.vehicleId(),
                        request.startAt(),
                        request.endAt(),
                        request.shiftType(),
                        request.notes(),
                        user
                )
        );
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SVO_SHIFTS_WRITE')")
    public SvoDutyShiftResponse cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return SvoDutyShiftResponse.from(shiftService.cancel(id, user));
    }
}
