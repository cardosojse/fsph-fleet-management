package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.CreateVehicleRequest;
import br.com.gestaofrota.api.v1.dtos.requests.RegisterVehicleDocumentRequest;
import br.com.gestaofrota.api.v1.dtos.requests.UpdateVehicleRequest;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleDocumentResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleMileageResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleUtilizationResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleResponse;
import br.com.gestaofrota.application.services.VehicleDocumentService;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.application.services.TripUtilizationService;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicles;
    private final VehicleDocumentService documents;
    private final TripUtilizationService tripUtilization;

    public VehicleController(VehicleService vehicles, VehicleDocumentService documents,
                             TripUtilizationService tripUtilization) {
        this.vehicles = vehicles;
        this.documents = documents;
        this.tripUtilization = tripUtilization;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VEHICLES_READ') and !hasRole('MOTORISTA')")
    public List<VehicleResponse> list() {
        return vehicles.list().stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('VEHICLES_ASSIGNED_READ')")
    public List<VehicleResponse> assignedToMe(@AuthenticationPrincipal AuthenticatedUser user) {
        return vehicles.assignedToUser(user.id()).stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLES_READ') and !hasRole('MOTORISTA')")
    public VehicleResponse find(@PathVariable UUID id) {
        return VehicleResponse.from(vehicles.find(id));
    }

    @GetMapping("/{id}/mileage")
    @PreAuthorize("hasAuthority('VEHICLES_READ') and !hasRole('MOTORISTA')")
    public VehicleMileageResponse mileage(@PathVariable UUID id) {
        return VehicleMileageResponse.from(vehicles.find(id));
    }

    @GetMapping("/{id}/utilization")
    @PreAuthorize("hasAuthority('TRIPS_READ') and !hasRole('MOTORISTA')")
    public VehicleUtilizationResponse utilization(
            @PathVariable UUID id,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return new VehicleUtilizationResponse(id, tripUtilization.calculateRate(id, startDate, endDate));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VEHICLES_WRITE')")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse result = VehicleResponse.from(vehicles.create(
                request.sectorId(),
                request.plate(),
                request.model(),
                request.capacity(),
                request.usageType(),
                request.currentOdometerKm()
            )
        );

        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLES_WRITE')")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request) {
        return VehicleResponse.from(
                vehicles.update(
                    id,
                    request.sectorId(),
                    request.plate(),
                    request.model(),
                    request.capacity(),
                    request.usageType(),
                    request.currentOdometerKm()
                )
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('VEHICLES_WRITE')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        vehicles.deactivate(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('VEHICLES_READ') and !hasRole('MOTORISTA')")
    public List<VehicleDocumentResponse> listDocuments(@PathVariable UUID id) {
        return documents.list(id).stream().map(VehicleDocumentResponse::from).toList();
    }

    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAuthority('VEHICLES_READ') and !hasRole('MOTORISTA')")
    public VehicleDocumentResponse findDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        return VehicleDocumentResponse.from(documents.find(id, documentId));
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('VEHICLES_WRITE')")
    public ResponseEntity<VehicleDocumentResponse> registerDocument(
            @PathVariable UUID id, @Valid @RequestBody RegisterVehicleDocumentRequest request
    ) {
        VehicleDocumentResponse result = VehicleDocumentResponse.from(
                documents.register(
                        id,
                        request.type(),
                        request.documentNumber(),
                        request.issuedOn(),
                        request.expiresOn()
                )
        );

        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + id + "/documents/" + result.id())).body(result);
    }
}
