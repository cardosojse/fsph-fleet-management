package br.com.gestaofrota;

import br.com.gestaofrota.api.v1.dtos.requests.*;
import br.com.gestaofrota.api.v1.dtos.responses.*;
import br.com.gestaofrota.application.services.*;
import br.com.gestaofrota.domain.enums.*;
import br.com.gestaofrota.domain.exceptions.*;
import br.com.gestaofrota.domain.models.*;
import br.com.gestaofrota.infra.repositories.*;
import br.com.gestaofrota.infra.config.startup.AdminProperties;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransportRouteIntegrationTests extends PostgresIntegrationTest {
    @Autowired private TransportService transports;
    @Autowired private RouteService routes;
    @Autowired private VehicleService vehicles;
    @Autowired private DriverService drivers;
    @Autowired private UserRepository users;
    @Autowired private SectorRepository sectors;
    @Autowired private RolePermissionService permissions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private AdminProperties adminProperties;
    @Autowired private tools.jackson.databind.ObjectMapper mapper;
    private final AtomicInteger planningSlots = new AtomicInteger();

    @Test
    void runsSeveralTransportsAndRepeatedStopsInOneRoute() throws Exception {
        Fixture f = fixture();
        AuthenticatedUser employee = actor(newUser(UserRole.EMPLOYEE));
        TransportResponse first = request(employee);
        TransportResponse second = request(f.admin());
        RouteResponse route = plan(f);
        RouteStopResponse one = routes.addStop(f.admin(), route.id(), new AddRouteStopRequest(first.id(), 1));
        RouteStopResponse two = routes.addStop(f.admin(), route.id(), new AddRouteStopRequest(second.id(), 2));
        RouteStopResponse three = routes.addStop(f.admin(), route.id(), new AddRouteStopRequest(first.id(), 3));

        assertThat(route.status()).isEqualTo(RouteStatus.PLANNED);
        assertThat(route.createdByUserId()).isEqualTo(f.admin().id());
        assertThat(first.requestedByUserId()).isEqualTo(employee.id());
        assertThat(first.sectorId()).isEqualTo(employee.sectorId());
        assertThat(f.driver().id()).isNotEqualTo(route.driverId());
        assertThat(routes.listStops(f.driver(), route.id())).extracting(RouteStopResponse::stopSequence)
                .containsExactly(1, 2, 3);
        assertThat(transports.find(employee, first.id()).status()).isEqualTo(TransportStatus.ASSIGNED);

        assertThatThrownBy(() -> routes.start(f.driver(), route.id(), 99)).isInstanceOf(ConflictException.class);
        assertThat(routes.start(f.driver(), route.id(), 100).status()).isEqualTo(RouteStatus.IN_PROGRESS);
        assertThatThrownBy(() -> routes.completeStop(f.driver(), route.id(), two.id()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> routes.complete(f.driver(), route.id(), 120)).isInstanceOf(ConflictException.class);
        routes.completeStop(f.driver(), route.id(), one.id());
        assertThat(transports.find(employee, first.id()).status()).isEqualTo(TransportStatus.ASSIGNED);
        routes.completeStop(f.driver(), route.id(), two.id());
        routes.completeStop(f.driver(), route.id(), three.id());
        assertThat(transports.find(employee, first.id()).status()).isEqualTo(TransportStatus.COMPLETED);
        assertThat(transports.find(f.admin(), second.id()).status()).isEqualTo(TransportStatus.COMPLETED);
        assertThatThrownBy(() -> routes.complete(f.driver(), route.id(), 99)).isInstanceOf(ConflictException.class);
        RouteResponse complete = routes.complete(f.driver(), route.id(), 120);
        assertThat(complete.status()).isEqualTo(RouteStatus.COMPLETED);
        assertThat(complete.realEndAt()).isAfterOrEqualTo(complete.realStartAt());
        assertThat(vehicles.find(f.vehicleId()).getCurrentOdometerKm()).isEqualTo(120);
        assertThatThrownBy(() -> routes.start(f.driver(), route.id(), 120)).isInstanceOf(ConflictException.class);
        String json = mvc.perform(get("/api/v1/routes/{id}", route.id()).with(user(f.driver())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("password", "password_hash");
    }

    @Test
    void cancellationKeepsHistoryAndReleasesUnservedTransports() {
        Fixture f = fixture();
        TransportResponse transport = request(f.admin());
        RouteResponse original = plan(f);
        RouteStopResponse stop = routes.addStop(f.admin(), original.id(), new AddRouteStopRequest(transport.id(), 1));
        assertThatThrownBy(() -> transports.cancel(f.admin(), transport.id())).isInstanceOf(ConflictException.class);
        assertThat(routes.cancel(f.admin(), original.id()).status()).isEqualTo(RouteStatus.CANCELLED);
        assertThat(routes.listStops(f.admin(), original.id())).singleElement()
                .extracting(RouteStopResponse::status).isEqualTo(RouteStopStatus.CANCELLED);
        assertThat(transports.find(f.admin(), transport.id()).status()).isEqualTo(TransportStatus.PENDING);

        RouteResponse replacement = plan(f);
        routes.addStop(f.admin(), replacement.id(), new AddRouteStopRequest(transport.id(), 1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM route_stops WHERE transport_id = ?",
                Integer.class, transport.id())).isEqualTo(2);
        assertThatThrownBy(() -> routes.cancelStop(f.admin(), replacement.id(), stop.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(transports.find(f.admin(), transport.id()).status()).isEqualTo(TransportStatus.ASSIGNED);
    }

    @Test
    void enforcesOwnershipPermissionsValidationAndStablePagination() throws Exception {
        Fixture f = fixture();
        AuthenticatedUser owner = actor(newUser(UserRole.EMPLOYEE));
        AuthenticatedUser stranger = actor(newUser(UserRole.EMPLOYEE));
        AuthenticatedUser anotherDriver = actor(newUser(UserRole.MOTORISTA));
        TransportResponse transport = request(owner);
        RouteResponse route = plan(f);
        mvc.perform(get("/api/v1/routes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/routes").with(user(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transports/{id}", transport.id()).with(user(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/transports/{id}/cancel", transport.id()).with(user(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/transports").with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/transports").with(user(stranger)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/routes/{id}", route.id()).with(user(anotherDriver)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/routes").with(user(anotherDriver)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/routes/{id}/start", route.id()).with(user(anotherDriver))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startMileage\":100}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/transports").with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/routes").with(user(f.admin()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transports?size=101").with(user(owner)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/transports/{id}/cancel", transport.id()).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void databaseEnforcesStopSequenceAndExecutionConstraints() {
        Fixture f = fixture();
        TransportResponse transport = request(f.admin());
        RouteResponse route = plan(f);
        routes.addStop(f.admin(), route.id(), new AddRouteStopRequest(transport.id(), 1));
        assertThatThrownBy(() -> routes.addStop(f.admin(), route.id(), new AddRouteStopRequest(transport.id(), 1)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO route_stops (route_id, transport_id, stop_sequence) VALUES (?, ?, 1)",
                route.id(), transport.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE routes SET status = 'COMPLETED' WHERE id = ?", route.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE transports SET status = 'REQUESTED' WHERE id = ?", transport.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('11','12','13','14','15')",
                Integer.class)).isEqualTo(5);
        assertThat(permissions.find(UserRole.EMPLOYEE)).contains(Permission.TRANSPORTS_READ, Permission.TRANSPORTS_REQUEST)
                .doesNotContain(Permission.ROUTES_MANAGE);
        assertThat(permissions.find(UserRole.GESTOR_FROTA)).contains(Permission.ROUTES_MANAGE, Permission.TRANSPORTS_MANAGE);
        assertThat(permissions.find(UserRole.MOTORISTA)).contains(Permission.ROUTES_READ, Permission.ROUTES_EXECUTE);
    }

    @Test
    void sharedTransportOnlyCompletesAfterItsStopsAcrossRoutesAreResolved() {
        Fixture f = fixture();
        TransportResponse transport = request(f.admin());
        RouteResponse first = plan(f);
        RouteResponse second = plan(f);
        RouteStopResponse firstStop = routes.addStop(f.admin(), first.id(), new AddRouteStopRequest(transport.id(), 1));
        routes.addStop(f.admin(), second.id(), new AddRouteStopRequest(transport.id(), 1));
        routes.start(f.driver(), first.id(), 100);
        routes.completeStop(f.driver(), first.id(), firstStop.id());
        assertThat(transports.find(f.admin(), transport.id()).status()).isEqualTo(TransportStatus.ASSIGNED);
        routes.cancel(f.admin(), second.id());
        assertThat(transports.find(f.admin(), transport.id()).status()).isEqualTo(TransportStatus.COMPLETED);
        routes.complete(f.driver(), first.id(), 110);
    }

    @Test
    void serializesConcurrentStartsSharingVehicleOrDriver() {
        Fixture f = fixture();
        Driver otherDriver = drivers.create(newUser(UserRole.MOTORISTA).getId());
        RouteResponse first = plan(f);
        RouteResponse sharedVehicle = routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), otherDriver.getId(),
                Instant.now().minusSeconds(7200), 60, null));
        raceStarts(f, first, sharedVehicle);

        Fixture g = fixture();
        RouteResponse another = plan(g);
        Vehicle secondVehicle = vehicles.create(null, plate(), "Van", 8, "Transporte", 100);
        RouteResponse sharedDriver = routes.create(g.admin(), new CreateRouteRequest(secondVehicle.getId(), another.driverId(),
                Instant.now().minusSeconds(7200), 60, null));
        raceStarts(g, another, sharedDriver);
    }

    @Test
    void exposesCreationAndExecutionThroughHttpWithoutTrustingActorIdsFromClient() throws Exception {
        Fixture f = fixture();
        AuthenticatedUser requester = actor(newUser(UserRole.EMPLOYEE));
        String transportJson = mvc.perform(post("/api/v1/transports").with(user(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateTransportRequest(Instant.now(), "Coleta", "A", "B"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestedByUserId").value(requester.id().toString()))
                .andExpect(jsonPath("$.sectorId").value(requester.sectorId().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID transportId = UUID.fromString(mapper.readTree(transportJson).get("id").asText());
        String routeJson = mvc.perform(post("/api/v1/routes").with(user(f.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateRouteRequest(f.vehicleId(), f.driverId(), Instant.now(), 60, null))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.createdByUserId").value(f.admin().id().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID routeId = UUID.fromString(mapper.readTree(routeJson).get("id").asText());
        mvc.perform(post("/api/v1/routes/{id}/stops", routeId).with(user(f.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AddRouteStopRequest(transportId, 0))))
                .andExpect(status().isBadRequest());
        String stopJson = mvc.perform(post("/api/v1/routes/{id}/stops", routeId).with(user(f.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AddRouteStopRequest(transportId, 1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID stopId = UUID.fromString(mapper.readTree(stopJson).get("id").asText());
        mvc.perform(get("/api/v1/routes/{id}/stops/{stopId}", routeId, stopId).with(user(f.driver())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transportId").value(transportId.toString()));
        mvc.perform(post("/api/v1/routes/{id}/start", routeId).with(user(f.driver()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startMileage\":100}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/v1/routes/{id}/stops/{stopId}/complete", routeId, stopId).with(user(f.driver())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post("/api/v1/routes/{id}/complete", routeId).with(user(f.driver()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endMileage\":110}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endMileage").value(110));
    }

    @Test
    void rejectsEmptyOrInactiveTripsAndCanCancelStopsDuringExecution() {
        Fixture f = fixture();
        RouteResponse route = plan(f);
        assertThatThrownBy(() -> routes.start(f.driver(), route.id(), 100)).isInstanceOf(ConflictException.class);
        TransportResponse transport = request(f.admin());
        RouteStopResponse stop = routes.addStop(f.admin(), route.id(), new AddRouteStopRequest(transport.id(), 1));
        routes.start(f.driver(), route.id(), 100);
        routes.cancelStop(f.driver(), route.id(), stop.id());
        assertThat(transports.find(f.admin(), transport.id()).status()).isEqualTo(TransportStatus.PENDING);
        assertThat(routes.complete(f.driver(), route.id(), 100).status()).isEqualTo(RouteStatus.COMPLETED);

        RouteResponse planned = plan(f);
        routes.addStop(f.admin(), planned.id(), new AddRouteStopRequest(transport.id(), 1));
        vehicles.deactivate(f.vehicleId());
        assertThatThrownBy(() -> routes.start(f.driver(), planned.id(), 100)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> plan(f)).isInstanceOf(ConflictException.class);
    }

    @Test
    void reservesDurationAndReturnAndAllowsAdjacentCancelledAndIndependentTrips() {
        Fixture f = fixture();
        Instant start = Instant.now().plusSeconds(86400).truncatedTo(ChronoUnit.SECONDS);
        RouteResponse first = routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 60, null));
        assertThat(first.plannedDurationMinutes()).isEqualTo(60);
        assertThat(first.plannedReturnAt()).isEqualTo(start.plusSeconds(3600));
        Driver otherDriver = drivers.create(newUser(UserRole.MOTORISTA).getId());
        Vehicle otherVehicle = vehicles.create(null, plate(), "Van", 8, "Transporte", 100);
        assertThatThrownBy(() -> routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), otherDriver.getId(),
                start.plusSeconds(1800), 60, null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> routes.create(f.admin(), new CreateRouteRequest(otherVehicle.getId(), f.driverId(),
                start.plusSeconds(1800), 60, null))).isInstanceOf(ConflictException.class);
        routes.create(f.admin(), new CreateRouteRequest(otherVehicle.getId(), otherDriver.getId(), start, 60, null));
        RouteResponse adjacent = routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(),
                first.plannedReturnAt(), null, start.plusSeconds(5400)));
        assertThat(adjacent.plannedDurationMinutes()).isEqualTo(30);
        routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), start.minusSeconds(3600), 60, start));
        routes.cancel(f.admin(), first.id());
        assertThat(routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 60, null)).status())
                .isEqualTo(RouteStatus.PLANNED);
    }

    @Test
    void updatesPlanningAtomicallyAndValidatesScheduleThroughHttp() throws Exception {
        Fixture f = fixture();
        Instant start = Instant.now().plusSeconds(86400).truncatedTo(ChronoUnit.SECONDS);
        RouteResponse first = routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 60, null));
        routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), start.plusSeconds(7200), 60, null));
        mvc.perform(put("/api/v1/routes/{id}/schedule", first.id()).with(user(f.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UpdateRouteScheduleRequest(start.plusSeconds(5400), 60, null))))
                .andExpect(status().isConflict());
        assertThat(routes.find(f.admin(), first.id()).plannedStartAt()).isEqualTo(start);
        mvc.perform(put("/api/v1/routes/{id}/schedule", first.id()).with(user(f.driver()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UpdateRouteScheduleRequest(start, 60, null))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/routes").with(user(f.admin())).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateRouteRequest(f.vehicleId(), f.driverId(), start, null, null))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/routes").with(user(f.admin())).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 60, start))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/routes").with(user(f.admin())).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 0, null))))
                .andExpect(status().isBadRequest());
        Instant moved = start.plusSeconds(14400);
        mvc.perform(put("/api/v1/routes/{id}/schedule", first.id()).with(user(f.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UpdateRouteScheduleRequest(moved, null, moved.plusSeconds(7200)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plannedDurationMinutes").value(120))
                .andExpect(jsonPath("$.plannedReturnAt").value(moved.plusSeconds(7200).toString()));
        routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 60, null));
        routes.addStop(f.admin(), first.id(), new AddRouteStopRequest(request(f.admin()).id(), 1));
        routes.start(f.driver(), first.id(), 100);
        assertThatThrownBy(() -> routes.reschedule(f.admin(), first.id(), new UpdateRouteScheduleRequest(moved, 60, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void serializesConcurrentReservationsSharingEitherResource() {
        Fixture f = fixture();
        Instant start = Instant.now().plusSeconds(86400);
        UUID otherDriver = drivers.create(newUser(UserRole.MOTORISTA).getId()).getId();
        raceReservations(f, new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 60, null),
                new CreateRouteRequest(f.vehicleId(), otherDriver, start, 60, null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM routes WHERE vehicle_id = ?", Integer.class, f.vehicleId()))
                .isEqualTo(1);

        Fixture g = fixture();
        UUID otherVehicle = vehicles.create(null, plate(), "Van", 8, "Transporte", 100).getId();
        raceReservations(g, new CreateRouteRequest(g.vehicleId(), g.driverId(), start, 60, null),
                new CreateRouteRequest(otherVehicle, g.driverId(), start, 60, null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM routes WHERE driver_id = ?", Integer.class, g.driverId()))
                .isEqualTo(1);
    }

    @Test
    void preservesLegacyRowsBlocksUnknownReservationsAndAllowsScheduleRepair() {
        Fixture f = fixture();
        Instant start = Instant.now().plusSeconds(86400).truncatedTo(ChronoUnit.SECONDS);
        UUID legacy = jdbc.queryForObject("""
                INSERT INTO routes (vehicle_id, driver_id, created_by_user_id, planned_start_at)
                VALUES (?, ?, ?, ?) RETURNING id
                """, UUID.class, f.vehicleId(), f.driverId(), f.admin().id(), Timestamp.from(start));
        RouteResponse old = routes.find(f.admin(), legacy);
        assertThat(old.plannedDurationMinutes()).isNull();
        assertThat(old.plannedReturnAt()).isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE routes SET planned_duration_minutes = 60 WHERE id = ?", legacy))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE routes SET planned_duration_minutes = 60, planned_return_at = planned_start_at + INTERVAL '2 hours'
                WHERE id = ?
                """, legacy)).isInstanceOf(DataIntegrityViolationException.class);
        routes.addStop(f.admin(), legacy, new AddRouteStopRequest(request(f.admin()).id(), 1));
        assertThatThrownBy(() -> routes.start(f.driver(), legacy, 100)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(),
                start.plusSeconds(86400), 60, null))).isInstanceOf(ConflictException.class);
        RouteResponse fixed = routes.reschedule(f.admin(), legacy, new UpdateRouteScheduleRequest(start, 60, null));
        assertThat(fixed.plannedReturnAt()).isEqualTo(start.plusSeconds(3600));
        routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), fixed.plannedReturnAt(), 60, null));
    }

    @Test
    void checksActualExecutionWindowForEarlyAndLateStarts() {
        Fixture f = fixture();
        Instant now = Instant.now();
        RouteResponse early = routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(),
                now.plusSeconds(7200), 60, null));
        RouteResponse occupiedNow = routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(),
                now.minusSeconds(600), 30, null));
        routes.addStop(f.admin(), early.id(), new AddRouteStopRequest(request(f.admin()).id(), 1));
        assertThatThrownBy(() -> routes.start(f.driver(), early.id(), 100)).isInstanceOf(ConflictException.class);
        routes.cancel(f.admin(), occupiedNow.id());
        routes.start(f.driver(), early.id(), 100);

        Fixture g = fixture();
        RouteResponse late = routes.create(g.admin(), new CreateRouteRequest(g.vehicleId(), g.driverId(),
                now.minusSeconds(1800), 60, null));
        routes.create(g.admin(), new CreateRouteRequest(g.vehicleId(), g.driverId(), late.plannedReturnAt(), 60, null));
        routes.addStop(g.admin(), late.id(), new AddRouteStopRequest(request(g.admin()).id(), 1));
        assertThatThrownBy(() -> routes.start(g.driver(), late.id(), 100)).isInstanceOf(ConflictException.class);
    }

    @Test
    void runningTripsReserveTheProjectedReturnAndOverdueResourcesStayBlocked() {
        Fixture f = fixture();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        RouteResponse running = routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(),
                now.minusSeconds(1800), 60, null));
        routes.addStop(f.admin(), running.id(), new AddRouteStopRequest(request(f.admin()).id(), 1));
        routes.start(f.driver(), running.id(), 100);
        assertThatThrownBy(() -> routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(),
                now.plusSeconds(2700), 15, null))).isInstanceOf(ConflictException.class);
        routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), now.plusSeconds(4500), 15, null));

        Fixture g = fixture();
        RouteResponse overdue = routes.create(g.admin(), new CreateRouteRequest(g.vehicleId(), g.driverId(),
                now.minusSeconds(7200), 60, null));
        RouteStopResponse stop = routes.addStop(g.admin(), overdue.id(), new AddRouteStopRequest(request(g.admin()).id(), 1));
        routes.start(g.driver(), overdue.id(), 100);
        jdbc.update("UPDATE routes SET real_start_at = planned_start_at WHERE id = ?", overdue.id());
        assertThatThrownBy(() -> routes.create(g.admin(), new CreateRouteRequest(g.vehicleId(), g.driverId(),
                now.plusSeconds(86400), 60, null))).isInstanceOf(ConflictException.class);
        routes.completeStop(g.driver(), overdue.id(), stop.id());
        routes.complete(g.driver(), overdue.id(), 100);
        routes.create(g.admin(), new CreateRouteRequest(g.vehicleId(), g.driverId(), now.plusSeconds(86400), 60, null));
    }

    private void raceReservations(Fixture f, CreateRouteRequest first, CreateRouteRequest second) {
        CompletableFuture<Boolean> a = CompletableFuture.supplyAsync(() -> tryReserve(f.admin(), first));
        CompletableFuture<Boolean> b = CompletableFuture.supplyAsync(() -> tryReserve(f.admin(), second));
        CompletableFuture.allOf(a, b).orTimeout(30, TimeUnit.SECONDS).join();
        assertThat(a.join() ^ b.join()).isTrue();
    }

    private boolean tryReserve(AuthenticatedUser actor, CreateRouteRequest request) {
        try { routes.create(actor, request); return true; }
        catch (ConflictException expected) { return false; }
    }

    private void raceStarts(Fixture f, RouteResponse first, RouteResponse second) {
        routes.addStop(f.admin(), first.id(), new AddRouteStopRequest(request(f.admin()).id(), 1));
        routes.addStop(f.admin(), second.id(), new AddRouteStopRequest(request(f.admin()).id(), 1));
        CompletableFuture<Boolean> a = CompletableFuture.supplyAsync(() -> tryStart(f.admin(), first.id()));
        CompletableFuture<Boolean> b = CompletableFuture.supplyAsync(() -> tryStart(f.admin(), second.id()));
        CompletableFuture.allOf(a, b).orTimeout(30, TimeUnit.SECONDS).join();
        assertThat(a.join() ^ b.join()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM routes WHERE id IN (?, ?) AND status = 'IN_PROGRESS'",
                Integer.class, first.id(), second.id())).isEqualTo(1);
    }

    private boolean tryStart(AuthenticatedUser admin, UUID routeId) {
        try { routes.start(admin, routeId, 100); return true; }
        catch (ConflictException expected) { return false; }
    }

    private Fixture fixture() {
        AuthenticatedUser admin = actor(users.findByEmailIgnoreCase(adminProperties.email()).orElseThrow());
        User motorist = newUser(UserRole.MOTORISTA);
        UUID driverId = drivers.create(motorist.getId()).getId();
        return new Fixture(admin, actor(motorist), driverId,
                vehicles.create(null, plate(), "Van", 8, "Transporte", 100).getId());
    }

    private RouteResponse plan(Fixture f) {
        Instant start = Instant.now().plusSeconds(7200L + planningSlots.getAndIncrement() * 7200L);
        return routes.create(f.admin(), new CreateRouteRequest(f.vehicleId(), f.driverId(), start, 60, null));
    }

    private TransportResponse request(AuthenticatedUser actor) {
        return transports.create(actor, new CreateTransportRequest(Instant.now(), "Coleta", "Unidade A", "Unidade B"));
    }

    private User newUser(UserRole role) {
        Sector sector = sectors.save(new Sector("Setor " + UUID.randomUUID()));
        return users.save(new User(sector, "Usuário " + role, UUID.randomUUID() + "@example.com", "hash", role));
    }

    private AuthenticatedUser actor(User u) {
        return new AuthenticatedUser(u.getId(), u.getSector().getId(), u.getName(), u.getEmail(), u.getPassword(),
                u.getRole(), u.isActive(), permissions.find(u.getRole()));
    }

    private String plate() { return UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase(); }
    private record Fixture(AuthenticatedUser admin, AuthenticatedUser driver, UUID driverId, UUID vehicleId) {}
}
