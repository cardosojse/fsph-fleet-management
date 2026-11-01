package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.RouteStatus;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteStatus status = RouteStatus.PLANNED;

    @Column(name = "planned_start_at", nullable = false)
    private Instant plannedStartAt;

    @Column(name = "real_start_at")
    private Instant realStartAt;

    @Column(name = "real_end_at")
    private Instant realEndAt;

    @Column(name = "start_mileage")
    private Long startMileage;

    @Column(name = "end_mileage")
    private Long endMileage;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Route() {}

    public Route(Vehicle vehicle, Driver driver, User creator, Instant plannedStartAt) {
        this.vehicle = vehicle;
        this.driver = driver;
        this.createdByUser = creator;
        this.plannedStartAt = plannedStartAt;
    }

    public void requirePlanned() {
        if (status != RouteStatus.PLANNED) {
            throw new ConflictException("A viagem deve estar planejada para esta operação");
        }
    }

    public void requireInProgress() {
        if (status != RouteStatus.IN_PROGRESS) {
            throw new ConflictException("A viagem deve estar em andamento para esta operação");
        }
    }

    public void start(long mileage, Instant now) {
        requirePlanned();
        if (mileage < 0 || mileage < vehicle.getCurrentOdometerKm()) {
            throw new ConflictException("Hodômetro inicial não pode ser menor que a quilometragem atual");
        }
        startMileage = mileage;
        realStartAt = now;
        status = RouteStatus.IN_PROGRESS;
    }

    public void complete(long mileage, Instant now) {
        requireInProgress();
        if (now.isBefore(realStartAt)) {
            throw new InvalidRequestException("O término não pode ser anterior ao início");
        }
        if (mileage < startMileage || mileage < vehicle.getCurrentOdometerKm()) {
            throw new ConflictException("Hodômetro final não pode reduzir a quilometragem");
        }
        endMileage = mileage;
        realEndAt = now;
        status = RouteStatus.COMPLETED;
    }

    public void cancel() {
        requirePlanned();
        status = RouteStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public Driver getDriver() { return driver; }
    public User getCreatedByUser() { return createdByUser; }
    public RouteStatus getStatus() { return status; }
    public Instant getPlannedStartAt() { return plannedStartAt; }
    public Instant getRealStartAt() { return realStartAt; }
    public Instant getRealEndAt() { return realEndAt; }
    public Long getStartMileage() { return startMileage; }
    public Long getEndMileage() { return endMileage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
