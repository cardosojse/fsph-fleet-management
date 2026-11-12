package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateSvoDutyShiftRequest(
        @NotNull(message = "A equipe é obrigatória")
        UUID teamId,

        UUID vehicleId,

        @NotNull(message = "A data/hora de início é obrigatória")
        Instant startAt,

        @NotNull(message = "A data/hora de término é obrigatória")
        Instant endAt,

        @Size(max = 50, message = "O tipo de plantão deve ter no máximo 50 caracteres")
        String shiftType,

        @Size(max = 500, message = "As observações devem ter no máximo 500 caracteres")
        String notes
) {
}
