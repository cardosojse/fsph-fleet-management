package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.Trip;
import br.com.gestaofrota.domain.enums.TripStatus;
import java.time.Instant;
import java.util.UUID;

public record TripResponse(UUID id, UUID vehicleId, UUID driverId, Instant startedAt, Instant endedAt,
                           long initialOdometerKm, Long finalOdometerKm, TripStatus status) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(trip.getId(), trip.getVehicle().getId(), trip.getDriver().getId(),
                trip.getStartedAt(), trip.getEndedAt(), trip.getInitialOdometerKm(),
                trip.getFinalOdometerKm(), trip.getStatus());
    }
}
