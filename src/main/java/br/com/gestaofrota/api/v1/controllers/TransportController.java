package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.CreateTransportRequest;
import br.com.gestaofrota.api.v1.dtos.responses.*;
import br.com.gestaofrota.application.services.TransportService;
import br.com.gestaofrota.domain.enums.TransportStatus;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transports")
public class TransportController {
    private final TransportService transports;
    public TransportController(TransportService transports) { this.transports = transports; }

    @PostMapping
    @PreAuthorize("hasAuthority('TRANSPORTS_REQUEST')")
    public ResponseEntity<TransportResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                                    @Valid @RequestBody CreateTransportRequest request) {
        TransportResponse response = transports.create(actor, request);
        return ResponseEntity.created(URI.create("/api/v1/transports/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRANSPORTS_READ', 'TRANSPORTS_MANAGE')")
    public PageResponse<TransportResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) TransportStatus status,
            @RequestParam(required = false) UUID sectorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return transports.list(actor, status, sectorId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TRANSPORTS_READ', 'TRANSPORTS_MANAGE')")
    public TransportResponse find(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return transports.find(actor, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('TRANSPORTS_REQUEST', 'TRANSPORTS_MANAGE')")
    public TransportResponse cancel(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return transports.cancel(actor, id);
    }
}
