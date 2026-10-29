package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.enums.MaintenanceType;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.domain.models.VehicleMaintenance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceResponse(
        UUID id,
        UUID vehicleId,
        String vehiclePlate,
        String vehicleModel,
        MaintenanceType type,
        MaintenanceStatus status,
        LocalDate performedOn,
        LocalDate completedOn,
        long odometerKm,
        Long completionOdometerKm,
        String description,
        String workshop,
        BigDecimal cost,
        Instant createdAt,
        Instant updatedAt
) {
    public static MaintenanceResponse from(VehicleMaintenance maintenance) {
        Vehicle vehicle = maintenance.getVehicle();
        return new MaintenanceResponse(
                maintenance.getId(),
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getModel(),
                maintenance.getType(),
                maintenance.getStatus(),
                maintenance.getPerformedOn(),
                maintenance.getCompletedOn(),
                maintenance.getOdometerKm(),
                maintenance.getCompletionOdometerKm(),
                maintenance.getDescription(),
                maintenance.getWorkshop(),
                maintenance.getCost(),
                maintenance.getCreatedAt(),
                maintenance.getUpdatedAt()
        );
    }
}

