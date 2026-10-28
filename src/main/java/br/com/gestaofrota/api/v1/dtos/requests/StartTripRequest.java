package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record StartTripRequest(
        @NotNull UUID vehicleId,
        @NotNull @PositiveOrZero Long initialOdometerKm
) { }
