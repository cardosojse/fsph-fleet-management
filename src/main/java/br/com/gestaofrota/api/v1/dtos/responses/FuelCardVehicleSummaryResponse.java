package br.com.gestaofrota.api.v1.dtos.responses;

import java.util.UUID;

public record FuelCardVehicleSummaryResponse(
        UUID id,
        String plate,
        String model
) {
}
