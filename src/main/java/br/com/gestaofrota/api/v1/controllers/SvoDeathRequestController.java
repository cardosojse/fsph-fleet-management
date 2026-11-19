package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.CancelSvoDeathRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CreateSvoDeathRequest;
import br.com.gestaofrota.api.v1.dtos.requests.TriageSvoDeathRequest;
import br.com.gestaofrota.api.v1.dtos.responses.SvoDeathRequestResponse;
import br.com.gestaofrota.application.services.SvoDeathRequestService;
import br.com.gestaofrota.domain.enums.SvoRequestStatus;
import br.com.gestaofrota.domain.models.SvoDeathRequest;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/svo/requests")
public class SvoDeathRequestController {

    private final SvoDeathRequestService requestService;

    public SvoDeathRequestController(SvoDeathRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SVO_REQUESTS_WRITE')")
    public ResponseEntity<SvoDeathRequestResponse> create(
            @RequestBody @Valid CreateSvoDeathRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        SvoDeathRequest saved = requestService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(SvoDeathRequestResponse.from(saved));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAuthority('SVO_REQUESTS_WRITE')")
    public ResponseEntity<Page<SvoDeathRequestResponse>> listMyRequests(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SvoDeathRequest> page = requestService.listMyRequests(actor, pageable);
        return ResponseEntity.ok(page.map(SvoDeathRequestResponse::from));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SVO_REQUESTS_READ')")
    public ResponseEntity<Page<SvoDeathRequestResponse>> listAll(
            @RequestParam(required = false) SvoRequestStatus status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SvoDeathRequest> page = requestService.listAll(status, city, from, to, search, pageable);
        return ResponseEntity.ok(page.map(SvoDeathRequestResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SVO_REQUESTS_READ', 'SVO_REQUESTS_WRITE')")
    public ResponseEntity<SvoDeathRequestResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        SvoDeathRequest deathRequest = requestService.findById(id, actor);
        return ResponseEntity.ok(SvoDeathRequestResponse.from(deathRequest));
    }

    @GetMapping("/protocol/{protocolNumber}")
    @PreAuthorize("hasAnyAuthority('SVO_REQUESTS_READ', 'SVO_REQUESTS_WRITE')")
    public ResponseEntity<SvoDeathRequestResponse> getByProtocol(
            @PathVariable String protocolNumber,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        SvoDeathRequest deathRequest = requestService.findByProtocolNumber(protocolNumber, actor);
        return ResponseEntity.ok(SvoDeathRequestResponse.from(deathRequest));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SVO_REQUESTS_WRITE')")
    public ResponseEntity<SvoDeathRequestResponse> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid CancelSvoDeathRequest cancelReq,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        SvoDeathRequest canceled = requestService.cancel(id, cancelReq, actor);
        return ResponseEntity.ok(SvoDeathRequestResponse.from(canceled));
    }

    @PatchMapping("/{id}/triage")
    @PreAuthorize("hasAuthority('SVO_REQUESTS_MANAGE')")
    public ResponseEntity<SvoDeathRequestResponse> triage(
            @PathVariable UUID id,
            @RequestBody @Valid TriageSvoDeathRequest triageReq,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        SvoDeathRequest triaged = requestService.triage(id, triageReq, actor);
        return ResponseEntity.ok(SvoDeathRequestResponse.from(triaged));
    }
}
