package br.com.gestaofrota.api.v1.dtos.requests;

import br.com.gestaofrota.domain.enums.FuelCardStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateFuelCardStatusRequest(
        @NotNull(message = "Status do cartão é obrigatório")
        FuelCardStatus status
) {
}
