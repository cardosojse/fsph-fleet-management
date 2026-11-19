package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.FuelCardStatus;
import br.com.gestaofrota.domain.models.FuelCard;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FuelCardResponse(
        UUID id,
        String cardNumber,
        String cardholderName,
        FuelCardStatus status,
        BigDecimal totalLimit,
        BigDecimal availableBalance,
        LocalDate expirationDate,
        FuelCardVehicleSummaryResponse vehicle,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static FuelCardResponse from(FuelCard card) {
        FuelCardVehicleSummaryResponse vehicleSummary = card.getVehicle() != null
                ? new FuelCardVehicleSummaryResponse(
                card.getVehicle().getId(),
                card.getVehicle().getPlate(),
                card.getVehicle().getModel()
        )
                : null;

        return new FuelCardResponse(
                card.getId(),
                card.getCardNumber(),
                card.getCardholderName(),
                card.getStatus(),
                card.getTotalLimit(),
                card.getAvailableBalance(),
                card.getExpirationDate(),
                vehicleSummary,
                card.getNotes(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
