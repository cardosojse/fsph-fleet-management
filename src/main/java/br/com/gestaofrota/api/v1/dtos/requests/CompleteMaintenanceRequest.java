package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CompleteMaintenanceRequest(
        LocalDate completedOn,
        @PositiveOrZero Long completionOdometerKm,
        @DecimalMin(value = "0.00") BigDecimal finalCost,
        @Size(max = 2000) String updatedDescription,
        UUID scheduleId
) {
}

