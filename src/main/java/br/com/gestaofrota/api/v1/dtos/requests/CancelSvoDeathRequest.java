package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.Size;

public record CancelSvoDeathRequest(
        @Size(max = 500, message = "Motivo do cancelamento não pode exceder 500 caracteres")
        String reason
) {
}
