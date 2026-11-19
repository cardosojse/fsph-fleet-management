package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.DeathLocationType;
import br.com.gestaofrota.domain.enums.SvoRequestStatus;
import br.com.gestaofrota.domain.models.SvoDeathRequest;

import java.time.Instant;
import java.util.UUID;

public record SvoDeathRequestResponse(
        UUID id,
        String protocolNumber,
        UUID requesterId,
        String requesterName,
        String requesterEmail,
        String deceasedName,
        Integer deceasedAge,
        String deceasedGender,
        Instant occurredAt,
        DeathLocationType deathLocationType,
        String address,
        String neighborhood,
        String city,
        String referencePoint,
        String contactName,
        String contactPhone,
        String relationshipWithDeceased,
        String presumedCircumstance,
        boolean hasSuspectedViolence,
        SvoRequestStatus status,
        String rejectionReason,
        UUID triagedById,
        String triagedByName,
        Instant triagedAt,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static SvoDeathRequestResponse from(SvoDeathRequest req) {
        return new SvoDeathRequestResponse(
                req.getId(),
                req.getProtocolNumber(),
                req.getRequester().getId(),
                req.getRequester().getName(),
                req.getRequester().getEmail(),
                req.getDeceasedName(),
                req.getDeceasedAge(),
                req.getDeceasedGender(),
                req.getOccurredAt(),
                req.getDeathLocationType(),
                req.getAddress(),
                req.getNeighborhood(),
                req.getCity(),
                req.getReferencePoint(),
                req.getContactName(),
                req.getContactPhone(),
                req.getRelationshipWithDeceased(),
                req.getPresumedCircumstance(),
                req.isHasSuspectedViolence(),
                req.getStatus(),
                req.getRejectionReason(),
                req.getTriagedBy() != null ? req.getTriagedBy().getId() : null,
                req.getTriagedBy() != null ? req.getTriagedBy().getName() : null,
                req.getTriagedAt(),
                req.getNotes(),
                req.getCreatedAt(),
                req.getUpdatedAt()
        );
    }
}
