package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TriageSvoDeathRequest(
        @NotNull(message = "Decisão de aprovação é obrigatória")
        Boolean approved,

        @Size(max = 500, message = "Justificativa da recusa não pode exceder 500 caracteres")
        String rejectionReason
) {
}
