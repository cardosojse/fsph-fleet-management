package br.com.gestaofrota.api.v1.dtos.responses;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleUtilizationResponse(UUID vehicleId, BigDecimal utilizationRatePercent) { }
