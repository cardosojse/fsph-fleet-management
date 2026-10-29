package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.MaintenanceAlertLevel;
import br.com.gestaofrota.domain.enums.MaintenanceScheduleStatus;
import br.com.gestaofrota.domain.models.PreventiveMaintenanceSchedule;
import br.com.gestaofrota.domain.models.Vehicle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record PreventiveScheduleResponse(
        UUID id,
        UUID vehicleId,
        String vehiclePlate,
        String vehicleModel,
        long currentVehicleOdometerKm,
        String serviceName,
        String notes,
        Long targetOdometerKm,
        Long remainingKm,
        LocalDate targetDate,
        Long remainingDays,
        long alertLeadKm,
        int alertLeadDays,
        MaintenanceScheduleStatus status,
        MaintenanceAlertLevel alertLevel,
        UUID completedMaintenanceId,
        LocalDate completedOn,
        Instant createdAt,
        Instant updatedAt
) {
    public static PreventiveScheduleResponse from(PreventiveMaintenanceSchedule schedule, LocalDate referenceDate) {
        Vehicle vehicle = schedule.getVehicle();
        long currentKm = vehicle.getCurrentOdometerKm();
        Long remainingKm = schedule.getTargetOdometerKm() == null
                ? null
                : schedule.getTargetOdometerKm() - currentKm;
        Long remainingDays = schedule.getTargetDate() == null
                ? null
                : ChronoUnit.DAYS.between(referenceDate, schedule.getTargetDate());
        MaintenanceAlertLevel alertLevel = schedule.evaluateAlertLevel(currentKm, referenceDate);

        return new PreventiveScheduleResponse(
                schedule.getId(),
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getModel(),
                currentKm,
                schedule.getServiceName(),
                schedule.getNotes(),
                schedule.getTargetOdometerKm(),
                remainingKm,
                schedule.getTargetDate(),
                remainingDays,
                schedule.getAlertLeadKm(),
                schedule.getAlertLeadDays(),
                schedule.getStatus(),
                alertLevel,
                schedule.getCompletedMaintenance() == null ? null : schedule.getCompletedMaintenance().getId(),
                schedule.getCompletedOn(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}

