package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.TransportStatus;
import br.com.gestaofrota.domain.models.Transport;
import java.time.Instant;
import java.util.UUID;

public record TransportResponse(UUID id, UUID sectorId, UUID requestedByUserId, Instant occurredAt,
        String serviceType, String pickupLocation, String destination, TransportStatus status,
        Instant createdAt, Instant updatedAt) {
    public static TransportResponse from(Transport t) {
        return new TransportResponse(t.getId(), t.getSector().getId(), t.getRequestedByUser().getId(),
                t.getOccurredAt(), t.getServiceType(), t.getPickupLocation(), t.getDestination(),
                t.getStatus(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
