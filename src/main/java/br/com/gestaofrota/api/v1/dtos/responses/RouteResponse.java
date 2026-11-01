package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.RouteStatus;
import br.com.gestaofrota.domain.models.Route;
import java.time.Instant;
import java.util.UUID;

public record RouteResponse(UUID id, UUID vehicleId, String vehiclePlate, UUID driverId,
        String driverName, UUID createdByUserId, RouteStatus status, Instant plannedStartAt,
        Instant realStartAt, Instant realEndAt, Long startMileage, Long endMileage,
        Instant createdAt, Instant updatedAt) {
    public static RouteResponse from(Route r) {
        return new RouteResponse(r.getId(), r.getVehicle().getId(), r.getVehicle().getPlate(),
                r.getDriver().getId(), r.getDriver().getUser().getName(), r.getCreatedByUser().getId(),
                r.getStatus(), r.getPlannedStartAt(), r.getRealStartAt(), r.getRealEndAt(),
                r.getStartMileage(), r.getEndMileage(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
