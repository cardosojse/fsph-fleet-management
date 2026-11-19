package br.com.gestaofrota.domain.models;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    protected AuditLog() {
    }

    public AuditLog(
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
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = Objects.requireNonNull(action, "Ação de auditoria é obrigatória").trim();
        this.resourceType = Objects.requireNonNull(resourceType, "Tipo de recurso é obrigatório").trim();
        this.resourceId = resourceId;
        this.details = details;
        this.clientIp = clientIp;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDetails() {
        return details;
    }

    public String getClientIp() {
        return clientIp;
    }
}
