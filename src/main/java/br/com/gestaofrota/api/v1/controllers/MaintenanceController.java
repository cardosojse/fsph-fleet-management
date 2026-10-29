package br.com.gestaofrota.api.v1.controllers;

import br.com.gestaofrota.api.v1.dtos.requests.CompleteMaintenanceRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CompletePreventiveScheduleRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CreateMaintenanceRequest;
import br.com.gestaofrota.api.v1.dtos.requests.CreatePreventiveScheduleRequest;
import br.com.gestaofrota.api.v1.dtos.responses.MaintenanceResponse;
import br.com.gestaofrota.api.v1.dtos.responses.PreventiveScheduleResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleMaintenanceSummaryResponse;
import br.com.gestaofrota.application.services.MaintenanceService;
import br.com.gestaofrota.domain.enums.MaintenanceScheduleStatus;
import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.enums.MaintenanceType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MaintenanceController {

    private final MaintenanceService maintenances;

    public MaintenanceController(MaintenanceService maintenances) {
        this.maintenances = maintenances;
    }

    @GetMapping("/maintenances")
    @PreAuthorize("hasAuthority('MAINTENANCES_READ')")
    public List<MaintenanceResponse> list(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) MaintenanceType type,
            @RequestParam(required = false) MaintenanceStatus status
    ) {
        return maintenances.list(vehicleId, type, status).stream()
                .map(MaintenanceResponse::from)
                .toList();
    }

    @GetMapping("/maintenances/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCES_READ')")
    public MaintenanceResponse find(@PathVariable UUID id) {
        return MaintenanceResponse.from(maintenances.find(id));
    }

    @PostMapping("/maintenances")
    @PreAuthorize("hasAuthority('MAINTENANCES_WRITE')")
    public ResponseEntity<MaintenanceResponse> register(@Valid @RequestBody CreateMaintenanceRequest request) {
        MaintenanceResponse result = MaintenanceResponse.from(
                maintenances.register(
                        request.vehicleId(),
                        request.type(),
                        request.status(),
                        request.performedOn(),
                        request.completedOn(),
                        request.odometerKm(),
                        request.completionOdometerKm(),
                        request.description(),
                        request.workshop(),
                        request.cost(),
                        request.scheduleId()
                )
        );
        return ResponseEntity.created(URI.create("/api/v1/maintenances/" + result.id())).body(result);
    }

    @PatchMapping("/maintenances/{id}/complete")
    @PreAuthorize("hasAuthority('MAINTENANCES_WRITE')")
    public MaintenanceResponse complete(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CompleteMaintenanceRequest request
    ) {
        CompleteMaintenanceRequest effective = request == null
                ? new CompleteMaintenanceRequest(null, null, null, null, null)
                : request;
        return MaintenanceResponse.from(
                maintenances.complete(
                        id,
                        effective.completedOn(),
                        effective.completionOdometerKm(),
                        effective.finalCost(),
                        effective.updatedDescription(),
                        effective.scheduleId()
                )
        );
    }

    @PatchMapping("/maintenances/{id}/cancel")
    @PreAuthorize("hasAuthority('MAINTENANCES_WRITE')")
    public MaintenanceResponse cancel(@PathVariable UUID id) {
        return MaintenanceResponse.from(maintenances.cancel(id));
    }

    @GetMapping("/vehicles/{vehicleId}/maintenances")
    @PreAuthorize("hasAuthority('MAINTENANCES_READ')")
    public List<MaintenanceResponse> listByVehicle(@PathVariable UUID vehicleId) {
        return maintenances.listByVehicle(vehicleId).stream()
                .map(MaintenanceResponse::from)
                .toList();
    }

    @GetMapping("/vehicles/{vehicleId}/maintenances/summary")
    @PreAuthorize("hasAuthority('MAINTENANCES_READ')")
    public VehicleMaintenanceSummaryResponse summarizeByVehicle(@PathVariable UUID vehicleId) {
        return maintenances.summarizeByVehicle(vehicleId);
    }

    @GetMapping("/maintenance-schedules")
    @PreAuthorize("hasAuthority('MAINTENANCES_READ')")
    public List<PreventiveScheduleResponse> listSchedules(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) MaintenanceScheduleStatus status
    ) {
        return maintenances.listSchedules(vehicleId, status);
    }

    @GetMapping("/maintenance-schedules/alerts")
    @PreAuthorize("hasAuthority('MAINTENANCES_READ')")
    public List<PreventiveScheduleResponse> listActiveAlerts() {
        return maintenances.listActiveAlerts();
    }

    @GetMapping("/maintenance-schedules/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCES_READ')")
    public PreventiveScheduleResponse findSchedule(@PathVariable UUID id) {
        return maintenances.findSchedule(id);
    }

    @PostMapping("/maintenance-schedules")
    @PreAuthorize("hasAuthority('MAINTENANCES_WRITE')")
    public ResponseEntity<PreventiveScheduleResponse> createSchedule(
            @Valid @RequestBody CreatePreventiveScheduleRequest request
    ) {
        PreventiveScheduleResponse result = PreventiveScheduleResponse.from(
                maintenances.createSchedule(
                        request.vehicleId(),
                        request.serviceName(),
                        request.notes(),
                        request.targetOdometerKm(),
                        request.targetDate(),
                        request.alertLeadKm(),
                        request.alertLeadDays()
                ),
                maintenances.currentReferenceDate()
        );
        return ResponseEntity.created(URI.create("/api/v1/maintenance-schedules/" + result.id())).body(result);
    }

    @PatchMapping("/maintenance-schedules/{id}/complete")
    @PreAuthorize("hasAuthority('MAINTENANCES_WRITE')")
    public PreventiveScheduleResponse completeSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CompletePreventiveScheduleRequest request
    ) {
        CompletePreventiveScheduleRequest effective = request == null
                ? new CompletePreventiveScheduleRequest(null, null)
                : request;
        return maintenances.completeSchedule(id, effective.completedMaintenanceId(), effective.completedOn());
    }

    @PatchMapping("/maintenance-schedules/{id}/cancel")
    @PreAuthorize("hasAuthority('MAINTENANCES_WRITE')")
    public PreventiveScheduleResponse cancelSchedule(@PathVariable UUID id) {
        return maintenances.cancelSchedule(id);
    }
}

