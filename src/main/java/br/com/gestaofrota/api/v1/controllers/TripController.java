package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.CompleteTripRequest;
import br.com.gestaofrota.api.v1.dtos.requests.StartTripRequest;
import br.com.gestaofrota.api.v1.dtos.responses.TripResponse;
import br.com.gestaofrota.api.v1.dtos.responses.TripHistoryResponse;
import br.com.gestaofrota.application.services.TripService;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {
    private final TripService trips;

    public TripController(TripService trips) { this.trips = trips; }

    @PostMapping
    @PreAuthorize("hasAuthority('TRIPS_WRITE')")
    public ResponseEntity<TripResponse> start(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody StartTripRequest request) {
        TripResponse result = TripResponse.from(trips.start(user.id(), request.vehicleId(), request.initialOdometerKm()));
        return ResponseEntity.created(URI.create("/api/v1/trips/" + result.id())).body(result);
    }

    @PatchMapping("/{tripId}/completion")
    @PreAuthorize("hasAuthority('TRIPS_WRITE')")
    public TripResponse complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID tripId,
                                 @Valid @RequestBody CompleteTripRequest request) {
        return TripResponse.from(trips.complete(user.id(), tripId, request.finalOdometerKm()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TRIPS_READ') and !hasRole('MOTORISTA')")
    public TripHistoryResponse history(@RequestParam(required = false) UUID vehicleId,
                                      @RequestParam(required = false) UUID driverId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedFrom,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startedTo,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("A página deve ser não negativa e o tamanho deve estar entre 1 e 100");
        }
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        return TripHistoryResponse.from(trips.history(vehicleId, driverId, startedFrom, startedTo, pageable));
    }
}
