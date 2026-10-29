package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.enums.MaintenanceType;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicle_maintenances")
public class VehicleMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceStatus status;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Column(name = "odometer_km", nullable = false)
    private long odometerKm;

    @Column(name = "completion_odometer_km")
    private Long completionOdometerKm;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 150)
    private String workshop;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cost;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VehicleMaintenance() {
    }

    private VehicleMaintenance(
            Vehicle vehicle,
            MaintenanceType type,
            MaintenanceStatus status,
            LocalDate performedOn,
            LocalDate completedOn,
            long odometerKm,
            Long completionOdometerKm,
            String description,
            String workshop,
            BigDecimal cost
    ) {
        this.vehicle = Objects.requireNonNull(vehicle, "Veículo é obrigatório");
        this.type = Objects.requireNonNull(type, "Tipo de manutenção é obrigatório");
        this.status = Objects.requireNonNull(status, "Status da manutenção é obrigatório");
        this.performedOn = Objects.requireNonNull(performedOn, "Data da manutenção é obrigatória");
        if (odometerKm < 0) {
            throw new InvalidRequestException("A quilometragem da manutenção não pode ser negativa");
        }
        if (completedOn != null && completedOn.isBefore(performedOn)) {
            throw new InvalidRequestException("A data de conclusão não pode ser anterior à data de realização");
        }
        if (completionOdometerKm != null && completionOdometerKm < odometerKm) {
            throw new InvalidRequestException("A quilometragem de conclusão não pode ser menor que a de entrada");
        }
        this.completedOn = completedOn;
        this.odometerKm = odometerKm;
        this.completionOdometerKm = completionOdometerKm;
        this.description = normalizeRequired(description, "Descrição da manutenção é obrigatória");
        this.workshop = normalizeRequired(workshop, "Oficina é obrigatória");
        this.cost = normalizeCost(cost);
    }

    public static VehicleMaintenance inProgress(
            Vehicle vehicle,
            MaintenanceType type,
            LocalDate performedOn,
            long odometerKm,
            String description,
            String workshop,
            BigDecimal cost
    ) {
        return new VehicleMaintenance(
                vehicle,
                type,
                MaintenanceStatus.IN_PROGRESS,
                performedOn,
                null,
                odometerKm,
                null,
                description,
                workshop,
                cost
        );
    }

    public static VehicleMaintenance completed(
            Vehicle vehicle,
            MaintenanceType type,
            LocalDate performedOn,
            LocalDate completedOn,
            long odometerKm,
            Long completionOdometerKm,
            String description,
            String workshop,
            BigDecimal cost
    ) {
        LocalDate effectiveCompletedOn = completedOn == null ? performedOn : completedOn;
        Long effectiveCompletionKm = completionOdometerKm == null ? odometerKm : completionOdometerKm;
        return new VehicleMaintenance(
                vehicle,
                type,
                MaintenanceStatus.COMPLETED,
                performedOn,
                effectiveCompletedOn,
                odometerKm,
                effectiveCompletionKm,
                description,
                workshop,
                cost
        );
    }

    public void complete(
            LocalDate completedOn,
            Long completionOdometerKm,
            BigDecimal finalCost,
            String updatedDescription
    ) {
        if (this.status != MaintenanceStatus.IN_PROGRESS) {
            throw new ConflictException("Apenas manutenções em andamento podem ser concluídas");
        }
        LocalDate effectiveCompletedOn = completedOn == null ? LocalDate.now() : completedOn;
        if (effectiveCompletedOn.isBefore(this.performedOn)) {
            throw new InvalidRequestException("A data de conclusão não pode ser anterior à data de realização");
        }
        long effectiveCompletionKm = completionOdometerKm == null ? this.odometerKm : completionOdometerKm;
        if (effectiveCompletionKm < this.odometerKm) {
            throw new InvalidRequestException("A quilometragem de conclusão não pode ser menor que a de entrada");
        }
        if (finalCost != null) {
            this.cost = normalizeCost(finalCost);
        }
        if (updatedDescription != null && !updatedDescription.isBlank()) {
            this.description = updatedDescription.trim();
        }
        this.completedOn = effectiveCompletedOn;
        this.completionOdometerKm = effectiveCompletionKm;
        this.status = MaintenanceStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status == MaintenanceStatus.CANCELED) {
            throw new ConflictException("A manutenção já está cancelada");
        }
        this.status = MaintenanceStatus.CANCELED;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public MaintenanceType getType() {
        return type;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public LocalDate getPerformedOn() {
        return performedOn;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public long getOdometerKm() {
        return odometerKm;
    }

    public Long getCompletionOdometerKm() {
        return completionOdometerKm;
    }

    public String getDescription() {
        return description;
    }

    public String getWorkshop() {
        return workshop;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(message);
        }
        return value.trim();
    }

    private static BigDecimal normalizeCost(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("O custo da manutenção deve ser maior ou igual a zero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
