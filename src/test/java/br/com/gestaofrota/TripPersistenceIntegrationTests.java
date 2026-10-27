package br.com.gestaofrota;

import br.com.gestaofrota.application.services.DriverService;
import br.com.gestaofrota.application.services.TripService;
import br.com.gestaofrota.application.services.TripVehicleAuthorization;
import br.com.gestaofrota.application.services.VehicleService;
import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.Trip;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.TripRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TripPersistenceIntegrationTests.IntegrationAuthorizationConfiguration.class)
class TripPersistenceIntegrationTests extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SectorRepository sectors;
    @Autowired private UserRepository users;
    @Autowired private DriverService drivers;
    @Autowired private VehicleService vehicles;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripService trips;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void migrationCreatesTripsTableAndPartialOpenTripIndex() {
        assertThat(jdbc.queryForObject("SELECT to_regclass('trips') IS NOT NULL", Boolean.class)).isTrue();
        String predicate = jdbc.queryForObject("""
                SELECT pg_get_expr(indpred, indrelid)
                FROM pg_index
                WHERE indexrelid = to_regclass('ux_trips_open_vehicle')
                """, String.class);
        assertThat(predicate).contains("status").contains("OPEN");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history WHERE version = '12' AND success
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void persistsCompletionAndUpdatesVehicleOdometer() {
        Motorist motorist = motorist();
        Vehicle vehicle = vehicle(1500);

        Trip started = trips.start(motorist.userId(), vehicle.getId(), 1500);
        Trip completed = trips.complete(motorist.userId(), started.getId(), 1585);

        assertThat(tripRepository.findById(completed.getId()).orElseThrow().getStatus())
                .isEqualTo(TripStatus.COMPLETED);
        assertThat(tripRepository.findById(completed.getId()).orElseThrow().getFinalOdometerKm())
                .isEqualTo(1585L);
        assertThat(vehicleRepository.findById(vehicle.getId()).orElseThrow().getCurrentOdometerKm())
                .isEqualTo(1585L);
    }

    @Test
    void databaseRejectsSecondOpenTripForVehicle() {
        Motorist motorist = motorist();
        Vehicle vehicle = vehicle(1500);
        trips.start(motorist.userId(), vehicle.getId(), 1500);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO trips (vehicle_id, driver_id, started_at, initial_odometer_km, status)
                VALUES (?, ?, ?, ?, 'OPEN')
                """, vehicle.getId(), motorist.driver().getId(), Timestamp.from(Instant.now()), 1500L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rollsBackTripCompletionAndVehicleOdometerTogether() {
        Motorist motorist = motorist();
        Vehicle vehicle = vehicle(1500);
        Trip started = trips.start(motorist.userId(), vehicle.getId(), 1500);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            trips.complete(motorist.userId(), started.getId(), 1585);
            status.setRollbackOnly();
        });

        Trip persisted = tripRepository.findById(started.getId()).orElseThrow();
        Vehicle persistedVehicle = vehicleRepository.findById(vehicle.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TripStatus.OPEN);
        assertThat(persisted.getFinalOdometerKm()).isNull();
        assertThat(persistedVehicle.getCurrentOdometerKm()).isEqualTo(1500L);
    }

    @Test
    void concurrentStartsLeaveAtMostOneOpenTripForVehicle() throws Exception {
        Motorist firstMotorist = motorist();
        Motorist secondMotorist = motorist();
        Vehicle vehicle = vehicle(1500);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> tryStartTogether(
                    firstMotorist, vehicle, ready, startTogether));
            Future<Boolean> second = executor.submit(() -> tryStartTogether(
                    secondMotorist, vehicle, ready, startTogether));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            List<Boolean> outcomes = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM trips WHERE vehicle_id = ? AND status = 'OPEN'
                    """, Integer.class, vehicle.getId())).isEqualTo(1);
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
        }
    }

    private boolean tryStartTogether(
            Motorist motorist,
            Vehicle vehicle,
            CountDownLatch ready,
            CountDownLatch startTogether
    ) throws InterruptedException {
        ready.countDown();
        if (!startTogether.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent start signal");
        }
        return tryStart(motorist, vehicle);
    }

    private boolean tryStart(Motorist motorist, Vehicle vehicle) {
        try {
            trips.start(motorist.userId(), vehicle.getId(), 1500);
            return true;
        } catch (ConflictException exception) {
            return false;
        }
    }

    private Vehicle vehicle(long odometerKm) {
        String plate = UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        return vehicles.create(null, plate, "Van", 8, "Transporte", odometerKm);
    }

    private Motorist motorist() {
        Sector sector = sectors.save(new Sector("Setor " + UUID.randomUUID()));
        User user = users.save(new User(sector, "Motorista", email(), "hash", UserRole.MOTORISTA));
        return new Motorist(drivers.create(user.getId()), user.getId());
    }

    private String email() {
        return "trip-" + UUID.randomUUID() + "@example.com";
    }

    private record Motorist(Driver driver, UUID userId) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class IntegrationAuthorizationConfiguration {
        @Bean
        TripVehicleAuthorization integrationAuthorization() {
            return (driverId, vehicleId) -> true;
        }
    }
}
