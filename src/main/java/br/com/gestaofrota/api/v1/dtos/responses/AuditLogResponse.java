package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String actorName,
        String actorEmail,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        String details,
        String clientIp
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOccurredAt(),
                log.getActorUserId(),
                log.getActorName(),
                log.getActorEmail(),
                log.getActorRole(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getDetails(),
                log.getClientIp()
        );
    }
}
