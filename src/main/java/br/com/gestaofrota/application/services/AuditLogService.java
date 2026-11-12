package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.models.AuditLog;
import br.com.gestaofrota.infra.repositories.AuditLogRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(
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
        // If actor info is missing, attempt to extract from current SecurityContext
        if (actorUserId == null && actorEmail == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                actorUserId = user.id();
                actorName = user.name();
                actorEmail = user.email();
                actorRole = user.role() != null ? user.role().name() : null;
            } else if (auth != null && auth.getPrincipal() instanceof UserDetails userDetails) {
                actorEmail = userDetails.getUsername();
                actorName = userDetails.getUsername();
            }
        }

        AuditLog auditLog = new AuditLog(
                actorUserId,
                actorName,
                actorEmail,
                actorRole,
                action,
                resourceType,
                resourceId,
                details,
                clientIp
        );
        return auditLogRepository.save(auditLog);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(
            AuthenticatedUser actor,
            String action,
            String resourceType,
            String resourceId,
            String details
    ) {
        return record(
                actor != null ? actor.id() : null,
                actor != null ? actor.name() : null,
                actor != null ? actor.email() : null,
                actor != null && actor.role() != null ? actor.role().name() : null,
                action,
                resourceType,
                resourceId,
                details,
                null
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog recordCurrent(
            String action,
            String resourceType,
            String resourceId,
            String details
    ) {
        return record(null, null, null, null, action, resourceType, resourceId, details, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> listLogs(
            Instant from,
            Instant to,
            UUID actorUserId,
            String action,
            String resourceType,
            Pageable pageable
    ) {
        return auditLogRepository.findFiltered(from, to, actorUserId, action, resourceType, pageable);
    }
}
