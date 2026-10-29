package br.com.gestaofrota.domain.models;

import br.com.gestaofrota.domain.enums.MaintenanceAlertLevel;
import br.com.gestaofrota.domain.enums.MaintenanceScheduleStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "preventive_maintenance_schedules")
public class PreventiveMaintenanceSchedule {

    public static final long DEFAULT_ALERT_LEAD_KM = 1000L;
    public static final int DEFAULT_ALERT_LEAD_DAYS = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "service_name", nullable = false, length = 150)
    private String serviceName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "target_odometer_km")
    private Long targetOdometerKm;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "alert_lead_km", nullable = false)
    private long alertLeadKm = DEFAULT_ALERT_LEAD_KM;

    @Column(name = "alert_lead_days", nullable = false)
    private int alertLeadDays = DEFAULT_ALERT_LEAD_DAYS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaintenanceScheduleStatus status = MaintenanceScheduleStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_maintenance_id")
    private VehicleMaintenance completedMaintenance;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PreventiveMaintenanceSchedule() {
    }

    public PreventiveMaintenanceSchedule(
            Vehicle vehicle,
            String serviceName,
            String notes,
            Long targetOdometerKm,
            LocalDate targetDate,
            Long alertLeadKm,
            Integer alertLeadDays
    ) {
        this.vehicle = Objects.requireNonNull(vehicle, "Veículo é obrigatório");
        if (serviceName == null || serviceName.isBlank()) {
            throw new InvalidRequestException("O nome do serviço preventivo é obrigatório");
        }
        if (targetOdometerKm == null && targetDate == null) {
            throw new InvalidRequestException("Informe a quilometragem-alvo ou a data-alvo da manutenção preventiva");
        }
        if (targetOdometerKm != null && targetOdometerKm <= 0) {
            throw new InvalidRequestException("A quilometragem-alvo deve ser maior que zero");
        }
        long resolvedLeadKm = alertLeadKm == null ? DEFAULT_ALERT_LEAD_KM : alertLeadKm;
        int resolvedLeadDays = alertLeadDays == null ? DEFAULT_ALERT_LEAD_DAYS : alertLeadDays;
        if (resolvedLeadKm < 0) {
            throw new InvalidRequestException("A antecedência de alerta em quilômetros não pode ser negativa");
        }
        if (resolvedLeadDays < 0) {
            throw new InvalidRequestException("A antecedência de alerta em dias não pode ser negativa");
        }
        this.serviceName = serviceName.trim();
        this.notes = (notes == null || notes.isBlank()) ? null : notes.trim();
        this.targetOdometerKm = targetOdometerKm;
        this.targetDate = targetDate;
        this.alertLeadKm = resolvedLeadKm;
        this.alertLeadDays = resolvedLeadDays;
    }

    public MaintenanceAlertLevel evaluateAlertLevel(long currentOdometerKm, LocalDate referenceDate) {
        if (this.status != MaintenanceScheduleStatus.PENDING) {
            return MaintenanceAlertLevel.ON_TIME;
        }
        LocalDate today = Objects.requireNonNull(referenceDate, "Data de referência é obrigatória");

        boolean kmOverdue = targetOdometerKm != null && currentOdometerKm >= targetOdometerKm;
        boolean dateOverdue = targetDate != null && targetDate.isBefore(today);
        if (kmOverdue || dateOverdue) {
            return MaintenanceAlertLevel.OVERDUE;
        }

        boolean kmDueSoon = targetOdometerKm != null
                && currentOdometerKm >= Math.max(0L, targetOdometerKm - alertLeadKm);
        boolean dateDueSoon = targetDate != null
                && !targetDate.isAfter(today.plusDays(alertLeadDays));
        if (kmDueSoon || dateDueSoon) {
            return MaintenanceAlertLevel.DUE_SOON;
        }

        return MaintenanceAlertLevel.ON_TIME;
    }

    public void markCompleted(VehicleMaintenance maintenance, LocalDate completedOn) {
        if (this.status != MaintenanceScheduleStatus.PENDING) {
            throw new ConflictException("Apenas programações pendentes podem ser concluídas");
        }
        this.completedMaintenance = maintenance;
        this.completedOn = completedOn == null ? LocalDate.now() : completedOn;
        this.status = MaintenanceScheduleStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status != MaintenanceScheduleStatus.PENDING) {
            throw new ConflictException("Apenas programações pendentes podem ser canceladas");
        }
        this.status = MaintenanceScheduleStatus.CANCELED;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getNotes() {
        return notes;
    }

    public Long getTargetOdometerKm() {
        return targetOdometerKm;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public long getAlertLeadKm() {
        return alertLeadKm;
    }

    public int getAlertLeadDays() {
        return alertLeadDays;
    }

    public MaintenanceScheduleStatus getStatus() {
        return status;
    }

    public VehicleMaintenance getCompletedMaintenance() {
        return completedMaintenance;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
