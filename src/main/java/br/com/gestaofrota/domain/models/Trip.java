package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "initial_odometer_km", nullable = false)
    private long initialOdometerKm;

    @Column(name = "final_odometer_km")
    private Long finalOdometerKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status;

    protected Trip() {
    }

    public Trip(Vehicle vehicle, Driver driver, Instant startedAt, long initialOdometerKm) {
        this.vehicle = Objects.requireNonNull(vehicle, "Veículo é obrigatório");
        this.driver = Objects.requireNonNull(driver, "Motorista é obrigatório");
        this.startedAt = Objects.requireNonNull(startedAt, "Instante inicial é obrigatório");
        if (initialOdometerKm < 0) {
            throw new InvalidRequestException("Quilometragem inicial não pode ser negativa");
        }
        this.initialOdometerKm = initialOdometerKm;
        this.status = TripStatus.OPEN;
    }

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public Driver getDriver() { return driver; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public long getInitialOdometerKm() { return initialOdometerKm; }
    public Long getFinalOdometerKm() { return finalOdometerKm; }
    public TripStatus getStatus() { return status; }

    public void complete(Instant endedAt, long finalOdometerKm) {
        if (status != TripStatus.OPEN) {
            throw new ConflictException("Viagem já encerrada");
        }
        Objects.requireNonNull(endedAt, "Instante final é obrigatório");
        if (endedAt.isBefore(startedAt)) {
            throw new InvalidRequestException("Instante final não pode ser anterior ao inicial");
        }
        if (finalOdometerKm < 0) {
            throw new InvalidRequestException("Quilometragem final não pode ser negativa");
        }
        if (finalOdometerKm < initialOdometerKm) {
            throw new InvalidRequestException("Quilometragem final não pode ser menor que a inicial");
        }

        this.endedAt = endedAt;
        this.finalOdometerKm = finalOdometerKm;
        this.status = TripStatus.COMPLETED;
    }
}
