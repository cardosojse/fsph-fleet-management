package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.*;
import br.com.gestaofrota.api.v1.dtos.responses.*;
import br.com.gestaofrota.application.services.RouteService;
import br.com.gestaofrota.domain.enums.RouteStatus;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {
    private final RouteService routes;
    public RouteController(RouteService routes) { this.routes = routes; }

    @PostMapping
    @PreAuthorize("hasAuthority('ROUTES_MANAGE')")
    public ResponseEntity<RouteResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                                @Valid @RequestBody CreateRouteRequest request) {
        RouteResponse response = routes.create(actor, request);
        return ResponseEntity.created(URI.create("/api/v1/routes/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROUTES_READ', 'ROUTES_MANAGE')")
    public PageResponse<RouteResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) RouteStatus status,
            @RequestParam(required = false) UUID vehicleId, @RequestParam(required = false) UUID driverId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return routes.list(actor, status, vehicleId, driverId, page, size);
    }

    @PutMapping("/{routeId}/schedule")
    @PreAuthorize("hasAuthority('ROUTES_MANAGE')")
    public RouteResponse reschedule(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID routeId, @Valid @RequestBody UpdateRouteScheduleRequest request) {
        return routes.reschedule(actor, routeId, request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROUTES_READ', 'ROUTES_MANAGE')")
    public RouteResponse find(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return routes.find(actor, id);
    }

    @GetMapping("/{routeId}/stops")
    @PreAuthorize("hasAnyAuthority('ROUTES_READ', 'ROUTES_MANAGE')")
    public List<RouteStopResponse> listStops(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID routeId) {
        return routes.listStops(actor, routeId);
    }

    @PostMapping("/{routeId}/stops")
    @PreAuthorize("hasAuthority('ROUTES_MANAGE')")
    public ResponseEntity<RouteStopResponse> addStop(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID routeId, @Valid @RequestBody AddRouteStopRequest request) {
        RouteStopResponse response = routes.addStop(actor, routeId, request);
        return ResponseEntity.created(URI.create("/api/v1/routes/" + routeId + "/stops/" + response.id())).body(response);
    }

    @GetMapping("/{routeId}/stops/{stopId}")
    @PreAuthorize("hasAnyAuthority('ROUTES_READ', 'ROUTES_MANAGE')")
    public RouteStopResponse findStop(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID routeId, @PathVariable UUID stopId) {
        return routes.findStop(actor, routeId, stopId);
    }

    @PostMapping("/{routeId}/start")
    @PreAuthorize("hasAuthority('ROUTES_EXECUTE')")
    public RouteResponse start(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID routeId,
                               @Valid @RequestBody StartRouteRequest request) {
        return routes.start(actor, routeId, request.startMileage());
    }

    @PostMapping("/{routeId}/complete")
    @PreAuthorize("hasAuthority('ROUTES_EXECUTE')")
    public RouteResponse complete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID routeId,
                                  @Valid @RequestBody CompleteRouteRequest request) {
        return routes.complete(actor, routeId, request.endMileage());
    }

    @PostMapping("/{routeId}/cancel")
    @PreAuthorize("hasAuthority('ROUTES_MANAGE')")
    public RouteResponse cancel(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID routeId) {
        return routes.cancel(actor, routeId);
    }

    @PostMapping("/{routeId}/stops/{stopId}/complete")
    @PreAuthorize("hasAuthority('ROUTES_EXECUTE')")
    public RouteStopResponse completeStop(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID routeId, @PathVariable UUID stopId) {
        return routes.completeStop(actor, routeId, stopId);
    }

    @PostMapping("/{routeId}/stops/{stopId}/cancel")
    @PreAuthorize("hasAnyAuthority('ROUTES_MANAGE', 'ROUTES_EXECUTE')")
    public RouteStopResponse cancelStop(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @PathVariable UUID routeId, @PathVariable UUID stopId) {
        return routes.cancelStop(actor, routeId, stopId);
    }
}
