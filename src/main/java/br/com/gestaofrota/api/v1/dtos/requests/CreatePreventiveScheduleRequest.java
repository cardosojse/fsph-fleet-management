package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreatePreventiveScheduleRequest(
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 150) String serviceName,
        @Size(max = 1000) String notes,
        @Positive Long targetOdometerKm,
        LocalDate targetDate,
        @PositiveOrZero Long alertLeadKm,
        @PositiveOrZero Integer alertLeadDays
) {
}

