package br.com.gestaofrota.domain.models;


import br.com.gestaofrota.domain.enums.VehicleStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    private Sector sector;

    @Column(nullable = false, length = 10)
    private String plate;

    @Column(nullable = false, length = 150)
    private String model;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "usage_type", nullable = false, length = 50)
    private String usageType;

    @Column(name = "current_odometer_km", nullable = false)
    private long currentOdometerKm;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vehicle() {
    }

    public Vehicle(Sector sector, String plate, String model, int capacity, String usageType, long currentOdometerKm) {
        this.sector = sector;
        this.plate = plate;
        this.model = model;
        this.capacity = capacity;
        this.usageType = usageType;
        this.currentOdometerKm = currentOdometerKm;
    }

    public UUID getId() {
        return id;
    }

    public Sector getSector() {
        return sector;
    }

    public String getPlate() {
        return plate;
    }

    public String getModel() {
        return model;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getUsageType() {
        return usageType;
    }

    public long getCurrentOdometerKm() {
        return currentOdometerKm;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(Sector sector, String plate, String model, int capacity, String usageType, long currentOdometerKm) {
        this.sector = sector;
        this.plate = plate;
        this.model = model;
        this.capacity = capacity;
        this.usageType = usageType;
        this.currentOdometerKm = currentOdometerKm;
    }

    public void updateCurrentOdometerKm(long currentOdometerKm) {
        if (currentOdometerKm < 0) {
            throw new InvalidRequestException("Quilometragem atual não pode ser negativa");
        }
        if (currentOdometerKm < this.currentOdometerKm) {
            throw new ConflictException("A quilometragem não pode ser menor que a atual");
        }
        this.currentOdometerKm = currentOdometerKm;
    }

    public void deactivate() {
        this.active = false;
    }
}
