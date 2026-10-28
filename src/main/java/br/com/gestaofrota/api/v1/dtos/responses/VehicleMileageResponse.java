package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.Vehicle;
import java.util.UUID;

public record VehicleMileageResponse(UUID vehicleId, long currentOdometerKm) {
    public static VehicleMileageResponse from(Vehicle vehicle) {
        return new VehicleMileageResponse(vehicle.getId(), vehicle.getCurrentOdometerKm());
    }
}
