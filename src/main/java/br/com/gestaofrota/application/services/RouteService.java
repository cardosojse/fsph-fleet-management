package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.*;
import br.com.gestaofrota.api.v1.dtos.responses.*;
import br.com.gestaofrota.domain.enums.*;
import br.com.gestaofrota.domain.exceptions.*;
import br.com.gestaofrota.domain.models.*;
import br.com.gestaofrota.infra.repositories.*;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RouteService {
    private final RouteRepository routes;
    private final RouteStopRepository stops;
    private final TransportRepository transports;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final UserRepository users;

    public RouteService(RouteRepository routes, RouteStopRepository stops, TransportRepository transports,
                        VehicleRepository vehicles, DriverRepository drivers, UserRepository users) {
        this.routes = routes;
        this.stops = stops;
        this.transports = transports;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.users = users;
    }

    @Transactional
    public RouteResponse create(AuthenticatedUser actor, CreateRouteRequest request) {
        TravelAccess.require(actor, Permission.ROUTES_MANAGE);
        Vehicle vehicle = lockVehicle(request.vehicleId());
        Driver driver = lockDriver(request.driverId());
        requireActiveResources(vehicle, driver);
        User creator = users.findById(actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", actor.id()));
        if (!creator.isActive()) throw new ConflictException("Criador da viagem inativo");
        return RouteResponse.from(routes.saveAndFlush(new Route(vehicle, driver, creator, request.plannedStartAt())));
    }

    public PageResponse<RouteResponse> list(AuthenticatedUser actor, RouteStatus status,
                                            UUID vehicleId, UUID driverId, int page, int size) {
        TravelAccess.requireRead(actor, Permission.ROUTES_READ, Permission.ROUTES_MANAGE);
        Specification<Route> spec = (root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            if (!TravelAccess.has(actor, Permission.ROUTES_MANAGE)) {
                filters.add(cb.equal(root.get("driver").get("user").get("id"), actor.id()));
            }
            if (status != null) filters.add(cb.equal(root.get("status"), status));
            if (vehicleId != null) filters.add(cb.equal(root.get("vehicle").get("id"), vehicleId));
            if (driverId != null) filters.add(cb.equal(root.get("driver").get("id"), driverId));
            return cb.and(filters.toArray(Predicate[]::new));
        };
        return PageResponse.from(routes.findAll(spec, TravelAccess.page(page, size)).map(RouteResponse::from));
    }

    public RouteResponse find(AuthenticatedUser actor, UUID id) {
        Route route = findRoute(id);
        requireRead(actor, route);
        return RouteResponse.from(route);
    }

    public List<RouteStopResponse> listStops(AuthenticatedUser actor, UUID routeId) {
        requireRead(actor, findRoute(routeId));
        return stops.findByRouteIdOrderByStopSequence(routeId).stream().map(RouteStopResponse::from).toList();
    }

    public RouteStopResponse findStop(AuthenticatedUser actor, UUID routeId, UUID stopId) {
        requireRead(actor, findRoute(routeId));
        return RouteStopResponse.from(findStop(routeId, stopId));
    }

    @Transactional
    public RouteStopResponse addStop(AuthenticatedUser actor, UUID routeId, AddRouteStopRequest request) {
        TravelAccess.require(actor, Permission.ROUTES_MANAGE);
        Route route = lockRoute(routeId);
        route.requirePlanned();
        Transport transport = lockTransport(request.transportId());
        if (request.stopSequence() == null || request.stopSequence() <= 0) {
            throw new InvalidRequestException("Sequência da parada deve ser positiva");
        }
        if (stops.existsByRouteIdAndStopSequence(routeId, request.stopSequence())) {
            throw new ConflictException("Já existe uma parada com esta sequência na viagem");
        }
        transport.assign();
        return RouteStopResponse.from(stops.saveAndFlush(new RouteStop(route, transport, request.stopSequence())));
    }

    @Transactional
    public RouteResponse start(AuthenticatedUser actor, UUID routeId, long mileage) {
        Route route = lockRoute(routeId);
        requireExecutor(actor, route);
        route.requirePlanned();
        // All execution paths lock the route, then vehicle, then driver.
        Vehicle vehicle = lockVehicle(route.getVehicle().getId());
        Driver driver = lockDriver(route.getDriver().getId());
        requireActiveResources(vehicle, driver);
        if (routes.existsByVehicleIdAndStatus(vehicle.getId(), RouteStatus.IN_PROGRESS)
                || routes.existsByDriverIdAndStatus(driver.getId(), RouteStatus.IN_PROGRESS)) {
            throw new ConflictException("Veículo ou motorista já está em outra viagem em andamento");
        }
        if (!stops.existsByRouteIdAndStatus(routeId, RouteStopStatus.PENDING)) {
            throw new ConflictException("A viagem precisa de uma parada pendente para iniciar");
        }
        route.start(mileage, Instant.now());
        vehicle.advanceOdometer(mileage);
        routes.flush();
        return RouteResponse.from(route);
    }

    @Transactional
    public RouteStopResponse completeStop(AuthenticatedUser actor, UUID routeId, UUID stopId) {
        Route route = lockRoute(routeId);
        requireExecutor(actor, route);
        route.requireInProgress();
        RouteStop stop = findStop(routeId, stopId);
        Transport transport = lockTransport(stop.getTransport().getId());
        RouteStop next = stops.findFirstByRouteIdAndStatusOrderByStopSequence(routeId, RouteStopStatus.PENDING)
                .orElseThrow(() -> new ConflictException("Não há paradas pendentes"));
        if (!next.getId().equals(stopId)) throw new ConflictException("As paradas devem ser concluídas na ordem");
        stop.complete();
        stops.flush();
        refreshTransport(transport);
        transports.flush();
        return RouteStopResponse.from(stop);
    }

    @Transactional
    public RouteStopResponse cancelStop(AuthenticatedUser actor, UUID routeId, UUID stopId) {
        Route route = lockRoute(routeId);
        if (route.getStatus() == RouteStatus.PLANNED) {
            TravelAccess.require(actor, Permission.ROUTES_MANAGE);
        } else {
            requireExecutor(actor, route);
            route.requireInProgress();
        }
        RouteStop stop = findStop(routeId, stopId);
        Transport transport = lockTransport(stop.getTransport().getId());
        stop.cancel();
        stops.flush();
        refreshTransport(transport);
        transports.flush();
        return RouteStopResponse.from(stop);
    }

    @Transactional
    public RouteResponse complete(AuthenticatedUser actor, UUID routeId, long mileage) {
        Route route = lockRoute(routeId);
        requireExecutor(actor, route);
        route.requireInProgress();
        Vehicle vehicle = lockVehicle(route.getVehicle().getId());
        lockDriver(route.getDriver().getId());
        if (stops.existsByRouteIdAndStatus(routeId, RouteStopStatus.PENDING)) {
            throw new ConflictException("Conclua ou cancele as paradas pendentes antes de encerrar a viagem");
        }
        route.complete(mileage, Instant.now());
        vehicle.advanceOdometer(mileage);
        routes.flush();
        return RouteResponse.from(route);
    }

    @Transactional
    public RouteResponse cancel(AuthenticatedUser actor, UUID routeId) {
        TravelAccess.require(actor, Permission.ROUTES_MANAGE);
        Route route = lockRoute(routeId);
        route.requirePlanned();
        List<RouteStop> pending = stops.findByRouteIdAndStatusOrderByTransportId(routeId, RouteStopStatus.PENDING);
        // A transport may belong to several routes. Lock shared transports in a stable order.
        List<Transport> affected = pending.stream().map(s -> s.getTransport().getId()).distinct().sorted()
                .map(this::lockTransport).toList();
        pending.forEach(RouteStop::cancel);
        stops.flush();
        affected.forEach(this::refreshTransport);
        route.cancel();
        routes.flush();
        return RouteResponse.from(route);
    }

    private void refreshTransport(Transport transport) {
        transport.refreshAssignment(
                stops.existsByTransportIdAndStatus(transport.getId(), RouteStopStatus.PENDING),
                stops.existsByTransportIdAndStatus(transport.getId(), RouteStopStatus.COMPLETED));
    }

    private void requireRead(AuthenticatedUser actor, Route route) {
        TravelAccess.requireRead(actor, Permission.ROUTES_READ, Permission.ROUTES_MANAGE);
        if (!TravelAccess.has(actor, Permission.ROUTES_MANAGE)
                && !route.getDriver().getUser().getId().equals(actor.id())) throw new UnauthorizedAccessException();
    }

    private void requireExecutor(AuthenticatedUser actor, Route route) {
        TravelAccess.require(actor, Permission.ROUTES_EXECUTE);
        if (actor.role() != UserRole.ADMIN && !route.getDriver().getUser().getId().equals(actor.id())) {
            throw new UnauthorizedAccessException();
        }
    }

    private void requireActiveResources(Vehicle vehicle, Driver driver) {
        if (!vehicle.isActive() || !driver.isActive() || !driver.getUser().isActive()
                || driver.getUser().getRole() != UserRole.MOTORISTA) {
            throw new ConflictException("Veículo e motorista devem estar ativos e o usuário deve possuir perfil MOTORISTA");
        }
    }

    private Route findRoute(UUID id) {
        return routes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Viagem", id));
    }
    private Route lockRoute(UUID id) {
        return routes.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Viagem", id));
    }
    private Transport lockTransport(UUID id) {
        return transports.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Transporte", id));
    }
    private Vehicle lockVehicle(UUID id) {
        return vehicles.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Veículo", id));
    }
    private Driver lockDriver(UUID id) {
        return drivers.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Motorista", id));
    }
    private RouteStop findStop(UUID routeId, UUID stopId) {
        return stops.findByIdAndRouteId(stopId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Parada", stopId));
    }
}
