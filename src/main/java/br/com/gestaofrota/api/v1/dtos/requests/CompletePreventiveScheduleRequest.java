package br.com.gestaofrota.api.v1.dtos.requests;

import java.time.LocalDate;
import java.util.UUID;

public record CompletePreventiveScheduleRequest(
        UUID completedMaintenanceId,
        LocalDate completedOn
) {
}

