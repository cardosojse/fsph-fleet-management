package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.Vehicle;
import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID sectorId,
        String plate,
        String model,
        int capacity,
        String usageType,
        long currentOdometerKm,
        boolean active,
        boolean inMaintenance,
        boolean available,
        Instant createdAt,
        Instant updatedAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getSector() == null ? null : vehicle.getSector().getId(),
                vehicle.getPlate(),
                vehicle.getModel(),
                vehicle.getCapacity(),
                vehicle.getUsageType(),
                vehicle.getCurrentOdometerKm(),
                vehicle.isActive(),
                vehicle.isInMaintenance(),
                vehicle.isAvailableForAllocation(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}
