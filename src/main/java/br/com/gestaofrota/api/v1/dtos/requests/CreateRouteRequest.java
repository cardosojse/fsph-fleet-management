package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateRouteRequest(
        @NotNull UUID vehicleId,
        @NotNull UUID driverId,
        @NotNull Instant plannedStartAt
) {}
