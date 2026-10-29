package br.com.gestaofrota.api.v1.dtos.requests;

import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.enums.MaintenanceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMaintenanceRequest(
        @NotNull UUID vehicleId,
        @NotNull MaintenanceType type,
        MaintenanceStatus status,
        @NotNull LocalDate performedOn,
        LocalDate completedOn,
        @NotNull @PositiveOrZero Long odometerKm,
        @PositiveOrZero Long completionOdometerKm,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 150) String workshop,
        @NotNull @DecimalMin(value = "0.00") BigDecimal cost,
        UUID scheduleId
) {
}

