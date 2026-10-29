package br.com.gestaofrota;

import br.com.gestaofrota.api.v1.dtos.responses.PreventiveScheduleResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleMaintenanceSummaryResponse;
import br.com.gestaofrota.application.services.MaintenanceAvailabilityGuard;
import br.com.gestaofrota.application.services.MaintenanceService;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.domain.enums.MaintenanceAlertLevel;
import br.com.gestaofrota.domain.enums.MaintenanceScheduleStatus;
import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.enums.MaintenanceType;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.models.PreventiveMaintenanceSchedule;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.domain.models.VehicleMaintenance;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MaintenanceIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    private SectorRepository sectors;

    @Autowired
    private VehicleService vehicles;

    @Autowired
    private MaintenanceService maintenances;

    @Autowired
    private MaintenanceAvailabilityGuard availabilityGuard;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void blocksVehicleWhileInMaintenanceAndReleasesOnCompletion() {
        Sector sector = sectors.save(new Sector("Frota " + UUID.randomUUID()));
        Vehicle inWorkshop = vehicles.create(sector.getId(), randomPlate(), "Master Ambulância", 4, "Saúde", 50_000L);
        Vehicle freeVehicle = vehicles.create(sector.getId(), randomPlate(), "Cronos", 5, "Administrativo", 12_000L);

        VehicleMaintenance opened = maintenances.register(
                inWorkshop.getId(),
                MaintenanceType.CORRECTIVE,
                MaintenanceStatus.IN_PROGRESS,
                LocalDate.now().minusDays(1),
                null,
                50_100L,
                null,
                "Reparo no sistema de arrefecimento",
                "Oficina Oficial FSPH",
                new BigDecimal("1800.00"),
                null
        );

        assertThat(vehicles.find(inWorkshop.getId()).isInMaintenance()).isTrue();
        assertThat(availabilityGuard.isVehicleInMaintenance(inWorkshop.getId())).isTrue();
        assertThat(vehicles.listAvailable())
                .extracting(Vehicle::getId)
                .contains(freeVehicle.getId())
                .doesNotContain(inWorkshop.getId());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(
                status -> availabilityGuard.assertVehicleAvailableForAllocation(inWorkshop.getId())
        )).isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO vehicle_maintenances (
                    vehicle_id, type, status, performed_on, odometer_km, description, workshop, cost
                ) VALUES (?, 'PREVENTIVE', 'IN_PROGRESS', ?, 50100, 'Duplicada', 'Oficina X', 100.00)
                """, inWorkshop.getId(), LocalDate.now()))
                .isInstanceOf(DataIntegrityViolationException.class);

        maintenances.complete(
                opened.getId(),
                LocalDate.now(),
                50_120L,
                new BigDecimal("1950.75"),
                "Reparo concluído com troca de mangueira e aditivo",
                null
        );

        Vehicle released = vehicles.find(inWorkshop.getId());
        assertThat(released.isInMaintenance()).isFalse();
        assertThat(released.isAvailableForAllocation()).isTrue();
        assertThat(released.getCurrentOdometerKm()).isEqualTo(50_120L);
        assertThat(vehicles.listAvailable()).extracting(Vehicle::getId).contains(inWorkshop.getId());
    }

    @Test
    void triggersPreventiveAlertsClosesScheduleAndComputesAccumulatedCosts() {
        Vehicle vehicle = vehicles.create(null, randomPlate(), "Hilux", 5, "Operacional", 19_500L);

        PreventiveMaintenanceSchedule kmSchedule = maintenances.createSchedule(
                vehicle.getId(),
                "Revisão de 20.000 km",
                "Troca de óleo e filtros",
                20_000L,
                null,
                1_000L,
                15
        );
        PreventiveMaintenanceSchedule overdueDateSchedule = maintenances.createSchedule(
                vehicle.getId(),
                "Alinhamento e balanceamento",
                null,
                null,
                LocalDate.now().minusDays(2),
                1_000L,
                15
        );

        List<PreventiveScheduleResponse> initialAlerts = maintenances.listActiveAlerts().stream()
                .filter(alert -> alert.vehicleId().equals(vehicle.getId()))
                .toList();
        assertThat(initialAlerts).hasSize(2);
        assertThat(initialAlerts.get(0).id()).isEqualTo(overdueDateSchedule.getId());
        assertThat(initialAlerts.get(0).alertLevel()).isEqualTo(MaintenanceAlertLevel.OVERDUE);
        assertThat(initialAlerts.get(1).id()).isEqualTo(kmSchedule.getId());
        assertThat(initialAlerts.get(1).alertLevel()).isEqualTo(MaintenanceAlertLevel.DUE_SOON);

        VehicleMaintenance completedPreventive = maintenances.register(
                vehicle.getId(),
                MaintenanceType.PREVENTIVE,
                MaintenanceStatus.COMPLETED,
                LocalDate.now(),
                LocalDate.now(),
                20_010L,
                20_010L,
                "Revisão programada de 20.000 km executada",
                "Concessionária Autorizada",
                new BigDecimal("920.00"),
                kmSchedule.getId()
        );

        maintenances.register(
                vehicle.getId(),
                MaintenanceType.CORRECTIVE,
                MaintenanceStatus.COMPLETED,
                LocalDate.now(),
                LocalDate.now(),
                20_050L,
                20_050L,
                "Troca de bateria",
                "Auto Elétrica Sergipe",
                new BigDecimal("580.50"),
                null
        );

        VehicleMaintenance canceled = maintenances.register(
                vehicle.getId(),
                MaintenanceType.CORRECTIVE,
                MaintenanceStatus.IN_PROGRESS,
                LocalDate.now(),
                null,
                20_050L,
                null,
                "Orçamento cancelado",
                "Auto Elétrica Sergipe",
                new BigDecimal("300.00"),
                null
        );
        maintenances.cancel(canceled.getId());

        PreventiveScheduleResponse updatedKmSchedule = maintenances.findSchedule(kmSchedule.getId());
        assertThat(updatedKmSchedule.status()).isEqualTo(MaintenanceScheduleStatus.COMPLETED);
        assertThat(updatedKmSchedule.completedMaintenanceId()).isEqualTo(completedPreventive.getId());

        VehicleMaintenanceSummaryResponse summary = maintenances.summarizeByVehicle(vehicle.getId());
        assertThat(summary.totalMaintenances()).isEqualTo(2);
        assertThat(summary.preventiveCost()).isEqualByComparingTo("920.00");
        assertThat(summary.correctiveCost()).isEqualByComparingTo("580.50");
        assertThat(summary.totalAccumulatedCost()).isEqualByComparingTo("1500.50");
        assertThat(summary.history()).hasSize(3);
    }

    private static String randomPlate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
    }
}

