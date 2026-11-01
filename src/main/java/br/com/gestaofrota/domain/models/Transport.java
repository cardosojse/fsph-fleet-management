package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.TransportStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transports")
public class Transport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedByUser;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType;

    @Column(name = "pickup_location", nullable = false, length = 500)
    private String pickupLocation;

    @Column(nullable = false, length = 500)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportStatus status = TransportStatus.PENDING;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transport() {}

    public Transport(User requester, Instant occurredAt, String serviceType, String pickupLocation, String destination) {
        this.sector = requester.getSector();
        this.requestedByUser = requester;
        this.occurredAt = occurredAt;
        this.serviceType = serviceType.trim();
        this.pickupLocation = pickupLocation.trim();
        this.destination = destination.trim();
    }

    public void assign() {
        if (status != TransportStatus.PENDING && status != TransportStatus.ASSIGNED) {
            throw new ConflictException("Somente transportes pendentes ou atribuídos podem receber paradas");
        }
        status = TransportStatus.ASSIGNED;
    }

    public void refreshAssignment(boolean hasPendingStops, boolean hasCompletedStops) {
        if (status == TransportStatus.CANCELLED) {
            throw new ConflictException("Transporte cancelado não pode ter atendimento alterado");
        }
        status = hasPendingStops ? TransportStatus.ASSIGNED
                : hasCompletedStops ? TransportStatus.COMPLETED : TransportStatus.PENDING;
    }

    public void cancel() {
        if (status != TransportStatus.PENDING) {
            throw new ConflictException("Somente transportes pendentes podem ser cancelados diretamente");
        }
        status = TransportStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public Sector getSector() { return sector; }
    public User getRequestedByUser() { return requestedByUser; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getServiceType() { return serviceType; }
    public String getPickupLocation() { return pickupLocation; }
    public String getDestination() { return destination; }
    public TransportStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
