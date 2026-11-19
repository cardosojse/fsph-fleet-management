package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.BindVehicleRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CreateFuelCardRequest;
import br.com.gestaofrota.api.v1.dtos.requests.UpdateFuelCardRequest;
import br.com.gestaofrota.api.v1.dtos.requests.UpdateFuelCardStatusRequest;
import br.com.gestaofrota.api.v1.dtos.responses.FuelCardResponse;
import br.com.gestaofrota.application.services.FuelCardService;
import br.com.gestaofrota.domain.enums.FuelCardStatus;
import br.com.gestaofrota.domain.models.FuelCard;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fuel-cards")
public class FuelCardController {

    private final FuelCardService fuelCardService;

    public FuelCardController(FuelCardService fuelCardService) {
        this.fuelCardService = fuelCardService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FUEL_CARDS_WRITE')")
    public ResponseEntity<FuelCardResponse> create(
            @RequestBody @Valid CreateFuelCardRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        FuelCard card = fuelCardService.create(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(FuelCardResponse.from(card));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_CARDS_READ')")
    public ResponseEntity<Page<FuelCardResponse>> list(
            @RequestParam(required = false) FuelCardStatus status,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) String search,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<FuelCard> page = fuelCardService.list(status, vehicleId, search, pageable);
        return ResponseEntity.ok(page.map(FuelCardResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_CARDS_READ')")
    public ResponseEntity<FuelCardResponse> getById(@PathVariable UUID id) {
        FuelCard card = fuelCardService.findById(id);
        return ResponseEntity.ok(FuelCardResponse.from(card));
    }

    @GetMapping("/by-number/{cardNumber}")
    @PreAuthorize("hasAuthority('FUEL_CARDS_READ')")
    public ResponseEntity<FuelCardResponse> getByCardNumber(@PathVariable String cardNumber) {
        FuelCard card = fuelCardService.findByCardNumber(cardNumber);
        return ResponseEntity.ok(FuelCardResponse.from(card));
    }

    @GetMapping("/by-vehicle/{vehicleId}")
    @PreAuthorize("hasAuthority('FUEL_CARDS_READ')")
    public ResponseEntity<FuelCardResponse> getByVehicleId(@PathVariable UUID vehicleId) {
        FuelCard card = fuelCardService.findByVehicleId(vehicleId);
        return ResponseEntity.ok(FuelCardResponse.from(card));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FUEL_CARDS_WRITE')")
    public ResponseEntity<FuelCardResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateFuelCardRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        FuelCard card = fuelCardService.update(id, request, actor);
        return ResponseEntity.ok(FuelCardResponse.from(card));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('FUEL_CARDS_WRITE')")
    public ResponseEntity<FuelCardResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateFuelCardStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        FuelCard card = fuelCardService.updateStatus(id, request.status(), actor);
        return ResponseEntity.ok(FuelCardResponse.from(card));
    }

    @PostMapping("/{id}/bind-vehicle")
    @PreAuthorize("hasAuthority('FUEL_CARDS_WRITE')")
    public ResponseEntity<FuelCardResponse> bindVehicle(
            @PathVariable UUID id,
            @RequestBody @Valid BindVehicleRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        FuelCard card = fuelCardService.bindVehicle(id, request.vehicleId(), actor);
        return ResponseEntity.ok(FuelCardResponse.from(card));
    }

    @DeleteMapping("/{id}/bind-vehicle")
    @PreAuthorize("hasAuthority('FUEL_CARDS_WRITE')")
    public ResponseEntity<FuelCardResponse> unbindVehicle(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser actor
    ) {
        FuelCard card = fuelCardService.unbindVehicle(id, actor);
        return ResponseEntity.ok(FuelCardResponse.from(card));
    }
}
