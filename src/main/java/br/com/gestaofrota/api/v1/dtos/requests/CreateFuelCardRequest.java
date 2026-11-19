package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFuelCardRequest(
        @NotBlank(message = "Número do cartão é obrigatório")
        @Size(min = 4, max = 50, message = "Número do cartão deve ter entre 4 e 50 caracteres")
        String cardNumber,

        @NotBlank(message = "Nome/identificador do titular ou cartão é obrigatório")
        @Size(max = 120, message = "Nome do titular não pode exceder 120 caracteres")
        String cardholderName,

        @NotNull(message = "Data de validade é obrigatória")
        LocalDate expirationDate,

        @NotNull(message = "Limite total é obrigatório")
        @PositiveOrZero(message = "Limite total deve ser maior ou igual a zero")
        BigDecimal totalLimit,

        @PositiveOrZero(message = "Saldo disponível deve ser maior ou igual a zero")
        BigDecimal availableBalance,

        UUID vehicleId,

        @Size(max = 500, message = "Observações não podem exceder 500 caracteres")
        String notes
) {
}
