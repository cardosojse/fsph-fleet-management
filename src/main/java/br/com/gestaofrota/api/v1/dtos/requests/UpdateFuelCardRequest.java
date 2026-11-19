package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFuelCardRequest(
        @NotBlank(message = "Nome/identificador do titular ou cartão é obrigatório")
        @Size(max = 120, message = "Nome do titular não pode exceder 120 caracteres")
        String cardholderName,

        @NotNull(message = "Data de validade é obrigatória")
        LocalDate expirationDate,

        @NotNull(message = "Limite total é obrigatório")
        @PositiveOrZero(message = "Limite total deve ser maior ou igual a zero")
        BigDecimal totalLimit,

        @NotNull(message = "Saldo disponível é obrigatório")
        @PositiveOrZero(message = "Saldo disponível deve ser maior ou igual a zero")
        BigDecimal availableBalance,

        @Size(max = 500, message = "Observações não podem exceder 500 caracteres")
        String notes
) {
}
