package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.responses.MaintenanceResponse;
import br.com.gestaofrota.api.v1.dtos.responses.PreventiveScheduleResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleMaintenanceSummaryResponse;
import br.com.gestaofrota.domain.enums.MaintenanceAlertLevel;
import br.com.gestaofrota.domain.enums.MaintenanceScheduleStatus;
import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.enums.MaintenanceType;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.PreventiveMaintenanceSchedule;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.domain.models.VehicleMaintenance;
import br.com.gestaofrota.infra.repositories.PreventiveMaintenanceScheduleRepository;
import br.com.gestaofrota.infra.repositories.VehicleMaintenanceRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class MaintenanceService implements MaintenanceAvailabilityGuard {

    private final VehicleMaintenanceRepository maintenances;
    private final PreventiveMaintenanceScheduleRepository schedules;
    private final VehicleRepository vehicles;
    private final Clock clock;

    @Autowired
    public MaintenanceService(
            VehicleMaintenanceRepository maintenances,
            PreventiveMaintenanceScheduleRepository schedules,
            VehicleRepository vehicles
    ) {
        this(maintenances, schedules, vehicles, Clock.systemDefaultZone());
    }

    MaintenanceService(
            VehicleMaintenanceRepository maintenances,
            PreventiveMaintenanceScheduleRepository schedules,
            VehicleRepository vehicles,
            Clock clock
    ) {
        this.maintenances = maintenances;
        this.schedules = schedules;
        this.vehicles = vehicles;
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<VehicleMaintenance> list(UUID vehicleId, MaintenanceType type, MaintenanceStatus status) {
        List<VehicleMaintenance> base = vehicleId == null
                ? maintenances.findAllByOrderByPerformedOnDescCreatedAtDesc()
                : listByVehicle(vehicleId);
        return base.stream()
                .filter(item -> type == null || item.getType() == type)
                .filter(item -> status == null || item.getStatus() == status)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VehicleMaintenance> listByVehicle(UUID vehicleId) {
        findVehicle(vehicleId);
        return maintenances.findByVehicleIdOrderByPerformedOnDescCreatedAtDesc(vehicleId);
    }

    @Transactional(readOnly = true)
    public VehicleMaintenance find(UUID id) {
        return maintenances.findWithVehicleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manutenção", id));
    }

    @Transactional
    public VehicleMaintenance register(
            UUID vehicleId,
            MaintenanceType type,
            MaintenanceStatus requestedStatus,
            LocalDate performedOn,
            LocalDate completedOn,
            long odometerKm,
            Long completionOdometerKm,
            String description,
            String workshop,
            BigDecimal cost,
            UUID scheduleId
    ) {
        MaintenanceStatus effectiveStatus = requestedStatus == null
                ? MaintenanceStatus.IN_PROGRESS
                : requestedStatus;
        if (effectiveStatus == MaintenanceStatus.CANCELED) {
            throw new InvalidRequestException("Não é possível registrar uma manutenção já cancelada");
        }

        Vehicle vehicle = lockVehicle(vehicleId);
        if (!vehicle.isActive()) {
            throw new ConflictException("Veículo inativo não pode receber registro de manutenção");
        }

        if (effectiveStatus == MaintenanceStatus.IN_PROGRESS) {
            if (scheduleId != null) {
                throw new InvalidRequestException("A programação preventiva só pode ser baixada por uma manutenção concluída");
            }
            if (completedOn != null || completionOdometerKm != null) {
                throw new InvalidRequestException("Dados de conclusão só podem ser informados em manutenções concluídas");
            }
            if (odometerKm < vehicle.getCurrentOdometerKm()) {
                throw new ConflictException("A quilometragem da manutenção em andamento não pode ser menor que a atual do veículo");
            }
            if (vehicle.isInMaintenance()
                    || maintenances.existsByVehicleIdAndStatus(vehicleId, MaintenanceStatus.IN_PROGRESS)) {
                throw new ConflictException("Veículo já possui manutenção em andamento");
            }

            VehicleMaintenance maintenance = VehicleMaintenance.inProgress(
                    vehicle,
                    type,
                    performedOn,
                    odometerKm,
                    description,
                    workshop,
                    cost
            );
            vehicle.startMaintenance();
            if (odometerKm > vehicle.getCurrentOdometerKm()) {
                vehicle.updateCurrentOdometerKm(odometerKm);
            }

            try {
                return maintenances.saveAndFlush(maintenance);
            } catch (DataIntegrityViolationException exception) {
                if (isInProgressUniqueViolation(exception)) {
                    throw new ConflictException("Veículo já possui manutenção em andamento");
                }
                throw exception;
            }
        }

        VehicleMaintenance maintenance = VehicleMaintenance.completed(
                vehicle,
                type,
                performedOn,
                completedOn,
                odometerKm,
                completionOdometerKm,
                description,
                workshop,
                cost
        );

        long maxReportedKm = Math.max(
                maintenance.getOdometerKm(),
                maintenance.getCompletionOdometerKm() == null ? maintenance.getOdometerKm() : maintenance.getCompletionOdometerKm()
        );
        if (maxReportedKm > vehicle.getCurrentOdometerKm()) {
            vehicle.updateCurrentOdometerKm(maxReportedKm);
        }

        VehicleMaintenance saved = maintenances.saveAndFlush(maintenance);
        if (scheduleId != null) {
            completeLinkedSchedule(scheduleId, saved);
        }
        return saved;
    }

    @Transactional
    public VehicleMaintenance complete(
            UUID maintenanceId,
            LocalDate completedOn,
            Long completionOdometerKm,
            BigDecimal finalCost,
            String updatedDescription,
            UUID scheduleId
    ) {
        VehicleMaintenance maintenance = maintenances.lockById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Manutenção", maintenanceId));
        Vehicle vehicle = lockVehicle(maintenance.getVehicle().getId());

        LocalDate resolvedCompletedOn = completedOn == null ? LocalDate.now(clock) : completedOn;
        long resolvedCompletionKm = completionOdometerKm == null
                ? Math.max(maintenance.getOdometerKm(), vehicle.getCurrentOdometerKm())
                : completionOdometerKm;

        if (resolvedCompletionKm < vehicle.getCurrentOdometerKm()) {
            throw new ConflictException("A quilometragem de conclusão não pode ser menor que a quilometragem atual do veículo");
        }

        maintenance.complete(resolvedCompletedOn, resolvedCompletionKm, finalCost, updatedDescription);
        if (resolvedCompletionKm > vehicle.getCurrentOdometerKm()) {
            vehicle.updateCurrentOdometerKm(resolvedCompletionKm);
        }
        vehicle.finishMaintenance();

        VehicleMaintenance saved = maintenances.saveAndFlush(maintenance);
        if (scheduleId != null) {
            completeLinkedSchedule(scheduleId, saved);
        }
        return saved;
    }

    @Transactional
    public VehicleMaintenance cancel(UUID maintenanceId) {
        VehicleMaintenance maintenance = maintenances.lockById(maintenanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Manutenção", maintenanceId));
        Vehicle vehicle = lockVehicle(maintenance.getVehicle().getId());

        boolean wasInProgress = maintenance.getStatus() == MaintenanceStatus.IN_PROGRESS;
        maintenance.cancel();
        if (wasInProgress) {
            vehicle.finishMaintenance();
        }
        return maintenances.saveAndFlush(maintenance);
    }

    @Transactional(readOnly = true)
    public VehicleMaintenanceSummaryResponse summarizeByVehicle(UUID vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        List<VehicleMaintenance> history = maintenances.findByVehicleIdOrderByPerformedOnDescCreatedAtDesc(vehicleId);

        int completedCount = 0;
        int inProgressCount = 0;
        int preventiveCount = 0;
        int correctiveCount = 0;
        BigDecimal preventiveCost = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal correctiveCost = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        LocalDate lastMaintenanceDate = null;
        Long lastMaintenanceOdometerKm = null;

        for (VehicleMaintenance item : history) {
            if (item.getStatus() == MaintenanceStatus.CANCELED) {
                continue;
            }
            if (item.getStatus() == MaintenanceStatus.COMPLETED) {
                completedCount++;
            } else if (item.getStatus() == MaintenanceStatus.IN_PROGRESS) {
                inProgressCount++;
            }

            if (item.getType() == MaintenanceType.PREVENTIVE) {
                preventiveCount++;
                preventiveCost = preventiveCost.add(item.getCost());
            } else {
                correctiveCount++;
                correctiveCost = correctiveCost.add(item.getCost());
            }

            if (lastMaintenanceDate == null || item.getPerformedOn().isAfter(lastMaintenanceDate)) {
                lastMaintenanceDate = item.getPerformedOn();
                lastMaintenanceOdometerKm = item.getCompletionOdometerKm() != null
                        ? item.getCompletionOdometerKm()
                        : item.getOdometerKm();
            }
        }

        BigDecimal totalAccumulatedCost = preventiveCost.add(correctiveCost).setScale(2, RoundingMode.HALF_UP);
        List<MaintenanceResponse> historyResponses = history.stream().map(MaintenanceResponse::from).toList();

        return new VehicleMaintenanceSummaryResponse(
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getModel(),
                vehicle.getCurrentOdometerKm(),
                vehicle.isInMaintenance(),
                completedCount + inProgressCount,
                completedCount,
                inProgressCount,
                preventiveCount,
                correctiveCount,
                totalAccumulatedCost,
                preventiveCost,
                correctiveCost,
                lastMaintenanceDate,
                lastMaintenanceOdometerKm,
                historyResponses
        );
    }

    @Transactional
    public PreventiveMaintenanceSchedule createSchedule(
            UUID vehicleId,
            String serviceName,
            String notes,
            Long targetOdometerKm,
            LocalDate targetDate,
            Long alertLeadKm,
            Integer alertLeadDays
    ) {
        Vehicle vehicle = lockVehicle(vehicleId);
        if (!vehicle.isActive()) {
            throw new ConflictException("Veículo inativo não pode receber programação preventiva");
        }
        PreventiveMaintenanceSchedule schedule = new PreventiveMaintenanceSchedule(
                vehicle,
                serviceName,
                notes,
                targetOdometerKm,
                targetDate,
                alertLeadKm,
                alertLeadDays
        );
        return schedules.saveAndFlush(schedule);
    }

    @Transactional(readOnly = true)
    public List<PreventiveScheduleResponse> listSchedules(UUID vehicleId, MaintenanceScheduleStatus status) {
        List<PreventiveMaintenanceSchedule> base;
        if (vehicleId != null) {
            findVehicle(vehicleId);
            base = schedules.findByVehicleIdOrderByCreatedAtDesc(vehicleId);
        } else {
            base = schedules.findAllByOrderByCreatedAtDesc();
        }
        LocalDate today = LocalDate.now(clock);
        return base.stream()
                .filter(item -> status == null || item.getStatus() == status)
                .map(item -> PreventiveScheduleResponse.from(item, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public PreventiveScheduleResponse findSchedule(UUID scheduleId) {
        PreventiveMaintenanceSchedule schedule = schedules.findWithVehicleById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Programação preventiva", scheduleId));
        return PreventiveScheduleResponse.from(schedule, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<PreventiveScheduleResponse> listActiveAlerts() {
        LocalDate today = LocalDate.now(clock);
        return schedules.findByStatusOrderByTargetDateAsc(MaintenanceScheduleStatus.PENDING).stream()
                .filter(schedule -> schedule.getVehicle().isActive())
                .map(schedule -> PreventiveScheduleResponse.from(schedule, today))
                .filter(response -> response.alertLevel() == MaintenanceAlertLevel.OVERDUE
                        || response.alertLevel() == MaintenanceAlertLevel.DUE_SOON)
                .sorted(Comparator
                        .comparingInt((PreventiveScheduleResponse r) -> r.alertLevel() == MaintenanceAlertLevel.OVERDUE ? 0 : 1)
                        .thenComparing(PreventiveScheduleResponse::remainingDays, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PreventiveScheduleResponse::remainingKm, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public PreventiveScheduleResponse completeSchedule(UUID scheduleId, UUID completedMaintenanceId, LocalDate completedOn) {
        PreventiveMaintenanceSchedule schedule = schedules.lockById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Programação preventiva", scheduleId));

        VehicleMaintenance linkedMaintenance = null;
        if (completedMaintenanceId != null) {
            linkedMaintenance = maintenances.findWithVehicleById(completedMaintenanceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Manutenção", completedMaintenanceId));
            validateScheduleMaintenanceLink(schedule, linkedMaintenance);
        }

        LocalDate resolvedCompletedOn = completedOn != null
                ? completedOn
                : (linkedMaintenance != null && linkedMaintenance.getCompletedOn() != null
                        ? linkedMaintenance.getCompletedOn()
                        : LocalDate.now(clock));
        schedule.markCompleted(linkedMaintenance, resolvedCompletedOn);
        return PreventiveScheduleResponse.from(schedules.saveAndFlush(schedule), LocalDate.now(clock));
    }

    @Transactional
    public PreventiveScheduleResponse cancelSchedule(UUID scheduleId) {
        PreventiveMaintenanceSchedule schedule = schedules.lockById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Programação preventiva", scheduleId));
        schedule.cancel();
        return PreventiveScheduleResponse.from(schedules.saveAndFlush(schedule), LocalDate.now(clock));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertVehicleAvailableForAllocation(UUID vehicleId) {
        Vehicle vehicle = lockVehicle(vehicleId);
        if (!vehicle.isActive()) {
            throw new ConflictException("Veículo inativo não está disponível para viagens ou agendamentos");
        }
        if (vehicle.isInMaintenance()
                || maintenances.existsByVehicleIdAndStatus(vehicleId, MaintenanceStatus.IN_PROGRESS)) {
            throw new ConflictException("Veículo em manutenção não está disponível para viagens ou agendamentos");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVehicleInMaintenance(UUID vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        return vehicle.isInMaintenance()
                || maintenances.existsByVehicleIdAndStatus(vehicleId, MaintenanceStatus.IN_PROGRESS);
    }

    public LocalDate currentReferenceDate() {
        return LocalDate.now(clock);
    }

    private void completeLinkedSchedule(UUID scheduleId, VehicleMaintenance maintenance) {
        PreventiveMaintenanceSchedule schedule = schedules.lockById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Programação preventiva", scheduleId));
        validateScheduleMaintenanceLink(schedule, maintenance);
        schedule.markCompleted(maintenance, maintenance.getCompletedOn());
        schedules.saveAndFlush(schedule);
    }

    private void validateScheduleMaintenanceLink(
            PreventiveMaintenanceSchedule schedule,
            VehicleMaintenance maintenance
    ) {
        if (!Objects.equals(schedule.getVehicle().getId(), maintenance.getVehicle().getId())) {
            throw new InvalidRequestException("A programação preventiva informada pertence a outro veículo");
        }
        if (maintenance.getType() != MaintenanceType.PREVENTIVE) {
            throw new InvalidRequestException("Apenas manutenções preventivas podem baixar uma programação preventiva");
        }
        if (maintenance.getStatus() != MaintenanceStatus.COMPLETED) {
            throw new InvalidRequestException("A programação preventiva só pode ser vinculada a uma manutenção concluída");
        }
    }

    private Vehicle findVehicle(UUID id) {
        return vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo", id));
    }

    private Vehicle lockVehicle(UUID id) {
        return vehicles.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo", id));
    }

    private boolean isInProgressUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return "ux_vehicle_maintenances_in_progress".equals(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}

