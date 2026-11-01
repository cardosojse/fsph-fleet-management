package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.RouteStopStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "route_stops")
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_id", nullable = false)
    private Transport transport;

    @Column(name = "stop_sequence", nullable = false)
    private int stopSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStopStatus status = RouteStopStatus.PENDING;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RouteStop() {}

    public RouteStop(Route route, Transport transport, int stopSequence) {
        this.route = route;
        this.transport = transport;
        this.stopSequence = stopSequence;
    }

    public void complete() {
        requirePending();
        status = RouteStopStatus.COMPLETED;
    }

    public void cancel() {
        requirePending();
        status = RouteStopStatus.CANCELLED;
    }

    private void requirePending() {
        if (status != RouteStopStatus.PENDING) {
            throw new ConflictException("Somente paradas pendentes podem ser alteradas");
        }
    }

    public UUID getId() { return id; }
    public Route getRoute() { return route; }
    public Transport getTransport() { return transport; }
    public int getStopSequence() { return stopSequence; }
    public RouteStopStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
