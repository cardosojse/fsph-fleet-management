package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.DeathLocationType;
import br.com.gestaofrota.domain.enums.SvoRequestStatus;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "svo_death_requests")
public class SvoDeathRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "protocol_number", nullable = false, unique = true, length = 30)
    private String protocolNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requester;

    @Column(name = "deceased_name", length = 150)
    private String deceasedName;

    @Column(name = "deceased_age")
    private Integer deceasedAge;

    @Column(name = "deceased_gender", length = 20)
    private String deceasedGender;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "death_location_type", nullable = false, length = 50)
    private DeathLocationType deathLocationType;

    @Column(nullable = false, length = 250)
    private String address;

    @Column(nullable = false, length = 100)
    private String neighborhood;

    @Column(nullable = false, length = 100)
    private String city = "Aracaju";

    @Column(name = "reference_point", length = 250)
    private String referencePoint;

    @Column(name = "contact_name", nullable = false, length = 150)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(name = "relationship_with_deceased", length = 100)
    private String relationshipWithDeceased;

    @Column(name = "presumed_circumstance", length = 500)
    private String presumedCircumstance;

    @Column(name = "has_suspected_violence", nullable = false)
    private boolean hasSuspectedViolence = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SvoRequestStatus status = SvoRequestStatus.PENDING_TRIAGE;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triaged_by_user_id")
    private User triagedBy;

    @Column(name = "triaged_at")
    private Instant triagedAt;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SvoDeathRequest() {}

    public SvoDeathRequest(
            String protocolNumber,
            User requester,
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
            Boolean hasSuspectedViolence,
            String notes
    ) {
        if (protocolNumber == null || protocolNumber.isBlank()) {
            throw new InvalidRequestException("Número de protocolo é obrigatório");
        }
        if (requester == null) {
            throw new InvalidRequestException("Usuário solicitante é obrigatório");
        }
        if (occurredAt == null) {
            throw new InvalidRequestException("Data/hora da ocorrência é obrigatória");
        }
        if (deathLocationType == null) {
            throw new InvalidRequestException("Tipo de local do óbito é obrigatório");
        }
        if (address == null || address.isBlank()) {
            throw new InvalidRequestException("Endereço da ocorrência é obrigatório");
        }
        if (neighborhood == null || neighborhood.isBlank()) {
            throw new InvalidRequestException("Bairro da ocorrência é obrigatório");
        }
        if (city == null || city.isBlank()) {
            throw new InvalidRequestException("Cidade da ocorrência é obrigatória");
        }
        if (contactName == null || contactName.isBlank()) {
            throw new InvalidRequestException("Nome do declarante/contato é obrigatório");
        }
        if (contactPhone == null || contactPhone.isBlank()) {
            throw new InvalidRequestException("Telefone do contato é obrigatório");
        }

        this.protocolNumber = protocolNumber.trim();
        this.requester = requester;
        this.deceasedName = deceasedName != null ? deceasedName.trim() : null;
        this.deceasedAge = deceasedAge;
        this.deceasedGender = deceasedGender != null ? deceasedGender.trim() : null;
        this.occurredAt = occurredAt;
        this.deathLocationType = deathLocationType;
        this.address = address.trim();
        this.neighborhood = neighborhood.trim();
        this.city = city.trim();
        this.referencePoint = referencePoint != null ? referencePoint.trim() : null;
        this.contactName = contactName.trim();
        this.contactPhone = contactPhone.trim();
        this.relationshipWithDeceased = relationshipWithDeceased != null ? relationshipWithDeceased.trim() : null;
        this.presumedCircumstance = presumedCircumstance != null ? presumedCircumstance.trim() : null;
        this.hasSuspectedViolence = Boolean.TRUE.equals(hasSuspectedViolence);
        this.notes = notes != null ? notes.trim() : null;
        this.status = SvoRequestStatus.PENDING_TRIAGE;
    }

    public UUID getId() {
        return id;
    }

    public String getProtocolNumber() {
        return protocolNumber;
    }

    public User getRequester() {
        return requester;
    }

    public String getDeceasedName() {
        return deceasedName;
    }

    public Integer getDeceasedAge() {
        return deceasedAge;
    }

    public String getDeceasedGender() {
        return deceasedGender;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public DeathLocationType getDeathLocationType() {
        return deathLocationType;
    }

    public String getAddress() {
        return address;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public String getCity() {
        return city;
    }

    public String getReferencePoint() {
        return referencePoint;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getRelationshipWithDeceased() {
        return relationshipWithDeceased;
    }

    public String getPresumedCircumstance() {
        return presumedCircumstance;
    }

    public boolean isHasSuspectedViolence() {
        return hasSuspectedViolence;
    }

    public SvoRequestStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public User getTriagedBy() {
        return triagedBy;
    }

    public Instant getTriagedAt() {
        return triagedAt;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void cancel(String reason) {
        if (this.status != SvoRequestStatus.PENDING_TRIAGE) {
            throw new InvalidRequestException("A solicitação não pode ser cancelada no status atual: " + this.status);
        }
        this.status = SvoRequestStatus.CANCELED;
        if (reason != null && !reason.isBlank()) {
            this.rejectionReason = "Cancelada pelo solicitante: " + reason.trim();
        }
    }

    public void triage(User triagedByUser, boolean approved, String reason) {
        if (this.status != SvoRequestStatus.PENDING_TRIAGE) {
            throw new InvalidRequestException("A solicitação já foi triada ou não está pendente: " + this.status);
        }
        if (triagedByUser == null) {
            throw new InvalidRequestException("Usuário responsável pela triagem é obrigatório");
        }

        this.triagedBy = triagedByUser;
        this.triagedAt = Instant.now();

        if (approved) {
            if (this.hasSuspectedViolence) {
                throw new InvalidRequestException("Casos com suspeita de violência ou causa externa são de competência exclusiva do IML e não podem ser aprovados pelo SVO");
            }
            this.status = SvoRequestStatus.TRIAGED;
            this.rejectionReason = null;
        } else {
            if (reason == null || reason.isBlank()) {
                throw new InvalidRequestException("Justificativa é obrigatória para recusa da solicitação");
            }
            this.status = SvoRequestStatus.REJECTED;
            this.rejectionReason = reason.trim();
        }
    }

    public void markOrderGenerated() {
        if (this.status != SvoRequestStatus.TRIAGED) {
            throw new InvalidRequestException("Apenas solicitações triadas e aprovadas podem gerar ordem de coleta");
        }
        this.status = SvoRequestStatus.ORDER_GENERATED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SvoDeathRequest that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
