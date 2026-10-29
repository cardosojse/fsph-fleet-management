package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.responses.PreventiveScheduleResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleMaintenanceSummaryResponse;
import br.com.gestaofrota.domain.enums.MaintenanceAlertLevel;
import br.com.gestaofrota.domain.enums.MaintenanceScheduleStatus;
import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.enums.MaintenanceType;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.models.PreventiveMaintenanceSchedule;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.domain.models.VehicleMaintenance;
import br.com.gestaofrota.infra.repositories.PreventiveMaintenanceScheduleRepository;
import br.com.gestaofrota.infra.repositories.VehicleMaintenanceRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTests {

    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 10, 28);

    @Mock
    private VehicleMaintenanceRepository maintenanceRepository;

    @Mock
    private PreventiveMaintenanceScheduleRepository scheduleRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private MaintenanceService maintenanceService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
        maintenanceService = new MaintenanceService(
                maintenanceRepository,
                scheduleRepository,
                vehicleRepository,
                clock
        );
    }

    @Test
    void opensInProgressMaintenanceMarksVehicleUnavailableAndAdvancesOdometer() {
        Vehicle vehicle = vehicleWithId("ABC1234", 10_000L);
        UUID vehicleId = vehicle.getId();

        when(vehicleRepository.lockById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.existsByVehicleIdAndStatus(vehicleId, MaintenanceStatus.IN_PROGRESS))
                .thenReturn(false);
        when(maintenanceRepository.saveAndFlush(any(VehicleMaintenance.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), UUID.randomUUID()));

        VehicleMaintenance created = maintenanceService.register(
                vehicleId,
                MaintenanceType.CORRECTIVE,
                MaintenanceStatus.IN_PROGRESS,
                FIXED_TODAY,
                null,
                10_250L,
                null,
                "Substituição de embreagem",
                "Oficina Central FSPH",
                new BigDecimal("1450.50"),
                null
        );

        assertThat(created.getStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(vehicle.isInMaintenance()).isTrue();
        assertThat(vehicle.isAvailableForAllocation()).isFalse();
        assertThat(vehicle.getCurrentOdometerKm()).isEqualTo(10_250L);

        assertThatThrownBy(() -> maintenanceService.assertVehicleAvailableForAllocation(vehicleId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("manutenção");
        assertThatThrownBy(vehicle::deactivate)
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsSecondSimultaneousInProgressMaintenance() {
        Vehicle vehicle = vehicleWithId("DEF5678", 20_000L);
        vehicle.startMaintenance();
        when(vehicleRepository.lockById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceService.register(
                vehicle.getId(),
                MaintenanceType.PREVENTIVE,
                MaintenanceStatus.IN_PROGRESS,
                FIXED_TODAY,
                null,
                20_000L,
                null,
                "Revisão geral",
                "Oficina Credenciada",
                new BigDecimal("500.00"),
                null
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void completesMaintenanceReleasesVehicleUpdatesOdometerCostAndLinkedSchedule() {
        Vehicle vehicle = vehicleWithId("GHI9012", 15_000L);
        vehicle.startMaintenance();

        VehicleMaintenance inProgress = withId(VehicleMaintenance.inProgress(
                vehicle,
                MaintenanceType.PREVENTIVE,
                FIXED_TODAY.minusDays(2),
                15_000L,
                "Revisão preventiva de 15.000 km",
                "Auto Center Prime",
                new BigDecimal("600.00")
        ), UUID.randomUUID());

        PreventiveMaintenanceSchedule schedule = withScheduleId(new PreventiveMaintenanceSchedule(
                vehicle,
                "Revisão de 15.000 km",
                "Trocar óleo e filtros",
                15_000L,
                null,
                1_000L,
                15
        ), UUID.randomUUID());

        when(maintenanceRepository.lockById(inProgress.getId())).thenReturn(Optional.of(inProgress));
        when(vehicleRepository.lockById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.saveAndFlush(any(VehicleMaintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduleRepository.lockById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(scheduleRepository.saveAndFlush(any(PreventiveMaintenanceSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleMaintenance completed = maintenanceService.complete(
                inProgress.getId(),
                FIXED_TODAY,
                15_040L,
                new BigDecimal("680.90"),
                "Revisão preventiva concluída com troca de filtro de cabine",
                schedule.getId()
        );

        assertThat(completed.getStatus()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(completed.getCost()).isEqualByComparingTo("680.90");
        assertThat(completed.getCompletionOdometerKm()).isEqualTo(15_040L);
        assertThat(vehicle.isInMaintenance()).isFalse();
        assertThat(vehicle.isAvailableForAllocation()).isTrue();
        assertThat(vehicle.getCurrentOdometerKm()).isEqualTo(15_040L);
        assertThat(schedule.getStatus()).isEqualTo(MaintenanceScheduleStatus.COMPLETED);
        assertThat(schedule.getCompletedMaintenance()).isEqualTo(completed);
    }

    @Test
    void cancelsInProgressMaintenanceAndReleasesVehicle() {
        Vehicle vehicle = vehicleWithId("JKL3456", 8_000L);
        vehicle.startMaintenance();
        VehicleMaintenance inProgress = withId(VehicleMaintenance.inProgress(
                vehicle,
                MaintenanceType.CORRECTIVE,
                FIXED_TODAY,
                8_000L,
                "Avaliação elétrica",
                "EletroAuto",
                new BigDecimal("200.00")
        ), UUID.randomUUID());

        when(maintenanceRepository.lockById(inProgress.getId())).thenReturn(Optional.of(inProgress));
        when(vehicleRepository.lockById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.saveAndFlush(any(VehicleMaintenance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleMaintenance canceled = maintenanceService.cancel(inProgress.getId());

        assertThat(canceled.getStatus()).isEqualTo(MaintenanceStatus.CANCELED);
        assertThat(vehicle.isInMaintenance()).isFalse();
        assertThat(vehicle.isAvailableForAllocation()).isTrue();
    }

    @Test
    void calculatesAccumulatedCostSummaryExcludingCanceledRecords() {
        Vehicle vehicle = vehicleWithId("MNO7890", 42_000L);

        VehicleMaintenance preventive = withId(VehicleMaintenance.completed(
                vehicle,
                MaintenanceType.PREVENTIVE,
                FIXED_TODAY.minusDays(30),
                FIXED_TODAY.minusDays(29),
                40_000L,
                40_010L,
                "Revisão 40k",
                "Oficina A",
                new BigDecimal("850.00")
        ), UUID.randomUUID());

        VehicleMaintenance corrective = withId(VehicleMaintenance.completed(
                vehicle,
                MaintenanceType.CORRECTIVE,
                FIXED_TODAY.minusDays(5),
                FIXED_TODAY.minusDays(4),
                41_800L,
                41_820L,
                "Troca de pastilhas e discos",
                "Oficina B",
                new BigDecimal("1230.40")
        ), UUID.randomUUID());

        VehicleMaintenance canceled = withId(VehicleMaintenance.inProgress(
                vehicle,
                MaintenanceType.CORRECTIVE,
                FIXED_TODAY.minusDays(2),
                41_900L,
                "Lançamento duplicado",
                "Oficina B",
                new BigDecimal("999.99")
        ), UUID.randomUUID());
        canceled.cancel();

        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(maintenanceRepository.findByVehicleIdOrderByPerformedOnDescCreatedAtDesc(vehicle.getId()))
                .thenReturn(List.of(canceled, corrective, preventive));

        VehicleMaintenanceSummaryResponse summary = maintenanceService.summarizeByVehicle(vehicle.getId());

        assertThat(summary.totalMaintenances()).isEqualTo(2);
        assertThat(summary.completedCount()).isEqualTo(2);
        assertThat(summary.inProgressCount()).isZero();
        assertThat(summary.preventiveCount()).isEqualTo(1);
        assertThat(summary.correctiveCount()).isEqualTo(1);
        assertThat(summary.preventiveCost()).isEqualByComparingTo("850.00");
        assertThat(summary.correctiveCost()).isEqualByComparingTo("1230.40");
        assertThat(summary.totalAccumulatedCost()).isEqualByComparingTo("2080.40");
        assertThat(summary.lastMaintenanceDate()).isEqualTo(FIXED_TODAY.minusDays(5));
        assertThat(summary.lastMaintenanceOdometerKm()).isEqualTo(41_820L);
        assertThat(summary.history()).hasSize(3);
    }

    @Test
    void evaluatesPreventiveAlertsByOdometerAndDateAndOrdersOverdueFirst() {
        Vehicle vehicle = vehicleWithId("PQR1122", 29_400L);

        PreventiveMaintenanceSchedule dueSoonByKm = withScheduleId(new PreventiveMaintenanceSchedule(
                vehicle,
                "Revisão de 30.000 km",
                null,
                30_000L,
                null,
                1_000L,
                15
        ), UUID.randomUUID());

        PreventiveMaintenanceSchedule overdueByDate = withScheduleId(new PreventiveMaintenanceSchedule(
                vehicle,
                "Troca anual de fluido de freio",
                null,
                null,
                FIXED_TODAY.minusDays(3),
                1_000L,
                15
        ), UUID.randomUUID());

        PreventiveMaintenanceSchedule onTime = withScheduleId(new PreventiveMaintenanceSchedule(
                vehicle,
                "Revisão de 40.000 km",
                null,
                40_000L,
                FIXED_TODAY.plusDays(90),
                1_000L,
                15
        ), UUID.randomUUID());

        when(scheduleRepository.findByStatusOrderByTargetDateAsc(MaintenanceScheduleStatus.PENDING))
                .thenReturn(List.of(dueSoonByKm, overdueByDate, onTime));

        List<PreventiveScheduleResponse> alerts = maintenanceService.listActiveAlerts();

        assertThat(alerts).hasSize(2);
        assertThat(alerts.get(0).id()).isEqualTo(overdueByDate.getId());
        assertThat(alerts.get(0).alertLevel()).isEqualTo(MaintenanceAlertLevel.OVERDUE);
        assertThat(alerts.get(0).remainingDays()).isEqualTo(-3L);

        assertThat(alerts.get(1).id()).isEqualTo(dueSoonByKm.getId());
        assertThat(alerts.get(1).alertLevel()).isEqualTo(MaintenanceAlertLevel.DUE_SOON);
        assertThat(alerts.get(1).remainingKm()).isEqualTo(600L);
    }

    @Test
    void rejectsPreventiveScheduleWithoutTargetKmOrTargetDate() {
        Vehicle vehicle = vehicleWithId("STU3344", 5_000L);
        when(vehicleRepository.lockById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> maintenanceService.createSchedule(
                vehicle.getId(),
                "Troca de óleo",
                null,
                null,
                null,
                1_000L,
                15
        )).isInstanceOf(InvalidRequestException.class);
    }

    private static Vehicle vehicleWithId(String plate, long odometerKm) {
        Vehicle vehicle = new Vehicle(new Sector("Transporte"), plate, "Sprinter", 12, "Operacional", odometerKm);
        ReflectionTestUtils.setField(vehicle, "id", UUID.randomUUID());
        return vehicle;
    }

    private static VehicleMaintenance withId(VehicleMaintenance maintenance, UUID id) {
        ReflectionTestUtils.setField(maintenance, "id", id);
        return maintenance;
    }

    private static PreventiveMaintenanceSchedule withScheduleId(PreventiveMaintenanceSchedule schedule, UUID id) {
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}

