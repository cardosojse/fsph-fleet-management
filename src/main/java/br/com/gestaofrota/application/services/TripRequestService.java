package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.responses.AvailableDriverResponse;
import br.com.gestaofrota.api.v1.dtos.responses.EligibleVehicleResponse;
import br.com.gestaofrota.api.v1.dtos.responses.FleetCalendarReservationResponse;
import br.com.gestaofrota.api.v1.dtos.responses.FleetCalendarResponse;
import br.com.gestaofrota.api.v1.dtos.responses.TripRequestAvailabilityResponse;
import br.com.gestaofrota.api.v1.dtos.responses.VehicleCalendarAvailabilityResponse;
import br.com.gestaofrota.domain.enums.RecordStatus;
import br.com.gestaofrota.domain.enums.TripRequestStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.exceptions.UnauthorizedAccessException;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.DriverLicense;
import br.com.gestaofrota.domain.models.TripRequest;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.DriverLicenseRepository;
import br.com.gestaofrota.infra.repositories.DriverRepository;
import br.com.gestaofrota.infra.repositories.TripRequestRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TripRequestService {

    private static final Set<TripRequestStatus> ACTIVE_RESERVATION_STATUSES =
            EnumSet.of(TripRequestStatus.APPROVED, TripRequestStatus.SCHEDULED);
    private static final Set<TripRequestStatus> DRIVER_RESERVED_STATUSES =
            EnumSet.of(TripRequestStatus.SCHEDULED);

    private final TripRequestRepository tripRequests;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final DriverLicenseRepository driverLicenses;
    private final MaintenanceAvailabilityGuard maintenanceGuard;
    private final NotificationService notifications;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public TripRequestService(
            TripRequestRepository tripRequests,
            UserRepository users,
            VehicleRepository vehicles,
            DriverRepository drivers,
            DriverLicenseRepository driverLicenses,
            MaintenanceAvailabilityGuard maintenanceGuard,
            NotificationService notifications,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AuditLogService auditLogService
    ) {
        this.tripRequests = tripRequests;
        this.users = users;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.driverLicenses = driverLicenses;
        this.maintenanceGuard = maintenanceGuard;
        this.notifications = notifications;
        this.auditLogService = auditLogService;
    }

    public TripRequestService(
            TripRequestRepository tripRequests,
            UserRepository users,
            VehicleRepository vehicles,
            DriverRepository drivers,
            DriverLicenseRepository driverLicenses,
            MaintenanceAvailabilityGuard maintenanceGuard,
            NotificationService notifications
    ) {
        this(tripRequests, users, vehicles, drivers, driverLicenses, maintenanceGuard, notifications, null);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listEligibleVehicles(int passengerCount, Instant departureAt, Instant expectedReturnAt) {
        if (passengerCount < 1) {
            throw new InvalidRequestException("O número de passageiros deve ser pelo menos 1");
        }
        if ((departureAt == null) != (expectedReturnAt == null)) {
            throw new InvalidRequestException("Informe tanto a data/hora de saída quanto a prevista de retorno");
        }
        if (departureAt != null && !expectedReturnAt.isAfter(departureAt)) {
            throw new InvalidRequestException("A data/hora prevista de retorno deve ser posterior à saída");
        }

        return findAvailableVehiclesForWindow(passengerCount, departureAt, expectedReturnAt, null);
    }

    @Transactional
    public TripRequest create(
            UUID requesterUserId,
            Instant departureAt,
            Instant expectedReturnAt,
            String origin,
            String destination,
            String purpose,
            int passengerCount,
            UUID requestedVehicleId
    ) {
        User requester = findUser(requesterUserId);
        validatePeriod(departureAt, expectedReturnAt);
        Vehicle requestedVehicle = resolveEligibleRequestedVehicle(
                requestedVehicleId,
                passengerCount,
                departureAt,
                expectedReturnAt,
                null
        );

        TripRequest saved = tripRequests.save(new TripRequest(
                requester,
                departureAt,
                expectedReturnAt,
                origin,
                destination,
                purpose,
                passengerCount,
                requestedVehicle
        ));

        if (auditLogService != null) {
            auditLogService.record(
                    requester.getId(),
                    requester.getName(),
                    requester.getEmail(),
                    requester.getRole() != null ? requester.getRole().name() : null,
                    "TRIP_REQUEST_CREATED",
                    "TRIP_REQUEST",
                    saved.getId().toString(),
                    "Solicitação de viagem criada de '" + origin + "' para '" + destination + "'",
                    null
            );
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TripRequest> listMine(UUID requesterUserId, TripRequestStatus status) {
        if (status != null) {
            return tripRequests.findByRequesterIdAndStatusOrderByCreatedAtDesc(requesterUserId, status);
        }
        return tripRequests.findByRequesterIdOrderByCreatedAtDesc(requesterUserId);
    }

    @Transactional(readOnly = true)
    public List<TripRequest> listAll(TripRequestStatus status) {
        if (status != null) {
            return tripRequests.findByStatusOrderByDepartureAtAsc(status);
        }
        return tripRequests.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public TripRequest findVisibleTo(UUID requestId, UUID actorUserId, boolean canReadAll) {
        TripRequest request = tripRequests.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação de viagem", requestId));
        if (!canReadAll && !request.getRequester().getId().equals(actorUserId)) {
            throw new UnauthorizedAccessException();
        }
        return request;
    }

    @Transactional
    public TripRequest updatePending(
            UUID requestId,
            UUID requesterUserId,
            Instant departureAt,
            Instant expectedReturnAt,
            String origin,
            String destination,
            String purpose,
            int passengerCount,
            UUID requestedVehicleId
    ) {
        TripRequest request = lockTripRequest(requestId);
        assertRequesterOwnership(request, requesterUserId);
        validatePeriod(departureAt, expectedReturnAt);

        Vehicle requestedVehicle = resolveEligibleRequestedVehicle(
                requestedVehicleId,
                passengerCount,
                departureAt,
                expectedReturnAt,
                request.getId()
        );

        request.updatePending(
                departureAt,
                expectedReturnAt,
                origin,
                destination,
                purpose,
                passengerCount,
                requestedVehicle
        );
        return request;
    }

    @Transactional
    public TripRequest cancelByRequester(UUID requestId, UUID requesterUserId) {
        TripRequest request = lockTripRequest(requestId);
        assertRequesterOwnership(request, requesterUserId);
        request.cancelByRequester(Instant.now());
        if (auditLogService != null) {
            auditLogService.recordCurrent(
                    "TRIP_REQUEST_CANCELED",
                    "TRIP_REQUEST",
                    request.getId().toString(),
                    "Solicitação de viagem #" + request.getId() + " cancelada pelo solicitante"
            );
        }
        return request;
    }

    @Transactional(readOnly = true)
    public TripRequestAvailabilityResponse analyzeAvailability(UUID requestId) {
        TripRequest request = tripRequests.findWithDetailsById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação de viagem", requestId));

        List<EligibleVehicleResponse> availableVehicles = findAvailableVehiclesForWindow(
                request.getPassengerCount(),
                request.getDepartureAt(),
                request.getExpectedReturnAt(),
                request.getId()
        ).stream().map(EligibleVehicleResponse::from).toList();

        List<AvailableDriverResponse> availableDrivers = findAvailableDriversForWindow(
                request.getDepartureAt(),
                request.getExpectedReturnAt(),
                request.getId()
        );

        UUID requestedVehicleId = request.getRequestedVehicle() != null
                ? request.getRequestedVehicle().getId()
                : null;
        boolean requestedVehicleAvailable = requestedVehicleId != null
                && availableVehicles.stream().anyMatch(vehicle -> vehicle.id().equals(requestedVehicleId));

        return new TripRequestAvailabilityResponse(
                request.getId(),
                request.getDepartureAt(),
                request.getExpectedReturnAt(),
                request.getPassengerCount(),
                requestedVehicleId,
                requestedVehicleAvailable,
                availableVehicles,
                availableDrivers
        );
    }

    @Transactional
    public TripRequest approve(
            UUID requestId,
            UUID managerUserId,
            UUID selectedVehicleId,
            String decisionNotes
    ) {
        TripRequest request = lockTripRequest(requestId);
        User decidedBy = findUser(managerUserId);

        UUID effectiveVehicleId = selectedVehicleId != null
                ? selectedVehicleId
                : (request.getRequestedVehicle() != null ? request.getRequestedVehicle().getId() : null);

        Vehicle preAssignedVehicle = effectiveVehicleId != null
                ? lockAndValidateVehicleForPeriod(
                effectiveVehicleId,
                request.getPassengerCount(),
                request.getDepartureAt(),
                request.getExpectedReturnAt(),
                request.getId()
        )
                : null;

        request.approve(decidedBy, preAssignedVehicle, decisionNotes, Instant.now());
        notifications.notifyTripRequestApproved(request);
        if (auditLogService != null) {
            auditLogService.record(
                    decidedBy.getId(),
                    decidedBy.getName(),
                    decidedBy.getEmail(),
                    decidedBy.getRole() != null ? decidedBy.getRole().name() : null,
                    "TRIP_REQUEST_APPROVED",
                    "TRIP_REQUEST",
                    request.getId().toString(),
                    "Solicitação de viagem #" + request.getId() + " aprovada",
                    null
            );
        }
        return request;
    }

    @Transactional
    public TripRequest reject(UUID requestId, UUID managerUserId, String rejectionReason) {
        TripRequest request = lockTripRequest(requestId);
        User decidedBy = findUser(managerUserId);
        request.reject(decidedBy, rejectionReason, Instant.now());
        notifications.notifyTripRequestRejected(request);
        if (auditLogService != null) {
            auditLogService.record(
                    decidedBy.getId(),
                    decidedBy.getName(),
                    decidedBy.getEmail(),
                    decidedBy.getRole() != null ? decidedBy.getRole().name() : null,
                    "TRIP_REQUEST_REJECTED",
                    "TRIP_REQUEST",
                    request.getId().toString(),
                    "Solicitação de viagem #" + request.getId() + " rejeitada. Motivo: " + rejectionReason,
                    null
            );
        }
        return request;
    }

    @Transactional
    public TripRequest schedule(
            UUID requestId,
            UUID managerUserId,
            UUID vehicleId,
            UUID driverId
    ) {
        TripRequest request = lockTripRequest(requestId);
        User scheduledBy = findUser(managerUserId);

        Vehicle lockedVehicle = lockAndValidateVehicleForPeriod(
                vehicleId,
                request.getPassengerCount(),
                request.getDepartureAt(),
                request.getExpectedReturnAt(),
                request.getId()
        );
        Driver lockedDriver = lockAndValidateDriverForPeriod(
                driverId,
                request.getDepartureAt(),
                request.getExpectedReturnAt(),
                request.getId()
        );

        request.schedule(scheduledBy, lockedVehicle, lockedDriver, Instant.now());
        notifications.notifyTripRequestScheduled(request);
        if (auditLogService != null) {
            auditLogService.record(
                    scheduledBy.getId(),
                    scheduledBy.getName(),
                    scheduledBy.getEmail(),
                    scheduledBy.getRole() != null ? scheduledBy.getRole().name() : null,
                    "TRIP_REQUEST_SCHEDULED",
                    "TRIP_REQUEST",
                    request.getId().toString(),
                    "Solicitação de viagem #" + request.getId() + " agendada com veículo "
                            + lockedVehicle.getPlate() + " e motorista " + lockedDriver.getUser().getName(),
                    null
            );
        }
        return request;
    }

    @Transactional(readOnly = true)
    public FleetCalendarResponse getFleetCalendar(Instant from, Instant to) {
        validatePeriod(from, to);

        List<Vehicle> activeVehicles = vehicles.findAll(Sort.by("plate")).stream()
                .filter(Vehicle::isActive)
                .toList();

        List<FleetCalendarReservationResponse> reservations = tripRequests
                .findActiveReservationsOverlapping(ACTIVE_RESERVATION_STATUSES, from, to)
                .stream()
                .filter(request -> request.getAssignedVehicle() != null)
                .map(FleetCalendarReservationResponse::from)
                .toList();

        Map<UUID, List<FleetCalendarReservationResponse>> reservationsByVehicle = reservations.stream()
                .collect(Collectors.groupingBy(FleetCalendarReservationResponse::vehicleId));

        List<VehicleCalendarAvailabilityResponse> vehicleItems = activeVehicles.stream()
                .map(vehicle -> {
                    List<FleetCalendarReservationResponse> vehicleReservations =
                            reservationsByVehicle.getOrDefault(vehicle.getId(), List.of());
                    boolean completelyFree = !vehicle.isInMaintenance() && vehicleReservations.isEmpty();
                    return new VehicleCalendarAvailabilityResponse(
                            vehicle.getId(),
                            vehicle.getPlate(),
                            vehicle.getModel(),
                            vehicle.getCapacity(),
                            vehicle.getAvailablePassengerSeats(),
                            vehicle.isActive(),
                            vehicle.isInMaintenance(),
                            completelyFree,
                            vehicleReservations
                    );
                })
                .toList();

        int inMaintenanceCount = (int) vehicleItems.stream()
                .filter(VehicleCalendarAvailabilityResponse::inMaintenance)
                .count();
        int withReservationsCount = (int) vehicleItems.stream()
                .filter(item -> !item.reservations().isEmpty())
                .count();
        int completelyFreeCount = (int) vehicleItems.stream()
                .filter(VehicleCalendarAvailabilityResponse::completelyFreeInPeriod)
                .count();

        return new FleetCalendarResponse(
                from,
                to,
                vehicleItems.size(),
                inMaintenanceCount,
                withReservationsCount,
                completelyFreeCount,
                reservations,
                vehicleItems
        );
    }

    private List<Vehicle> findAvailableVehiclesForWindow(
            int passengerCount,
            Instant departureAt,
            Instant expectedReturnAt,
            UUID excludeRequestId
    ) {
        Set<UUID> reservedVehicleIds = departureAt == null
                ? Set.of()
                : tripRequests.findActiveReservationsOverlapping(ACTIVE_RESERVATION_STATUSES, departureAt, expectedReturnAt)
                .stream()
                .filter(existing -> !existing.getId().equals(excludeRequestId))
                .map(TripRequest::getAssignedVehicle)
                .filter(vehicle -> vehicle != null)
                .map(Vehicle::getId)
                .collect(Collectors.toSet());

        return vehicles.findByActiveTrueAndInMaintenanceFalseOrderByPlateAsc().stream()
                .filter(vehicle -> vehicle.supportsPassengers(passengerCount))
                .filter(vehicle -> !reservedVehicleIds.contains(vehicle.getId()))
                .toList();
    }

    private List<AvailableDriverResponse> findAvailableDriversForWindow(
            Instant departureAt,
            Instant expectedReturnAt,
            UUID excludeRequestId
    ) {
        Set<UUID> reservedDriverIds = tripRequests
                .findActiveReservationsOverlapping(DRIVER_RESERVED_STATUSES, departureAt, expectedReturnAt)
                .stream()
                .filter(existing -> !existing.getId().equals(excludeRequestId))
                .map(TripRequest::getAssignedDriver)
                .filter(driver -> driver != null)
                .map(Driver::getId)
                .collect(Collectors.toSet());

        LocalDate expectedReturnDate = expectedReturnAt.atZone(ZoneOffset.UTC).toLocalDate();

        return driverLicenses.findByStatus(RecordStatus.ACTIVE).stream()
                .filter(license -> !license.getExpiresOn().isBefore(expectedReturnDate))
                .filter(license -> {
                    Driver driver = license.getDriver();
                    return driver != null
                            && driver.isActive()
                            && driver.getUser() != null
                            && driver.getUser().isActive()
                            && !reservedDriverIds.contains(driver.getId());
                })
                .map(license -> AvailableDriverResponse.from(license.getDriver(), license))
                .sorted(Comparator.comparing(AvailableDriverResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Vehicle lockAndValidateVehicleForPeriod(
            UUID vehicleId,
            int passengerCount,
            Instant departureAt,
            Instant expectedReturnAt,
            UUID excludeRequestId
    ) {
        Vehicle lockedVehicle = vehicles.lockById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo", vehicleId));
        maintenanceGuard.assertVehicleAvailableForAllocation(vehicleId);
        if (!lockedVehicle.supportsPassengers(passengerCount)) {
            throw new InvalidRequestException(
                    "O veículo selecionado comporta no máximo "
                            + lockedVehicle.getAvailablePassengerSeats()
                            + " passageiro(s), insuficiente para "
                            + passengerCount
                            + " passageiro(s)"
            );
        }
        boolean hasOverlap = tripRequests.findVehicleReservationsOverlapping(
                        vehicleId,
                        ACTIVE_RESERVATION_STATUSES,
                        departureAt,
                        expectedReturnAt
                )
                .stream()
                .anyMatch(existing -> !existing.getId().equals(excludeRequestId));
        if (hasOverlap) {
            throw new ConflictException("O veículo selecionado já possui viagem aprovada ou agendada sobreposta neste período");
        }
        return lockedVehicle;
    }

    private Driver lockAndValidateDriverForPeriod(
            UUID driverId,
            Instant departureAt,
            Instant expectedReturnAt,
            UUID excludeRequestId
    ) {
        Driver lockedDriver = drivers.lockById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Motorista", driverId));
        if (!lockedDriver.isActive() || lockedDriver.getUser() == null || !lockedDriver.getUser().isActive()) {
            throw new ConflictException("O motorista selecionado está inativo");
        }

        LocalDate expectedReturnDate = expectedReturnAt.atZone(ZoneOffset.UTC).toLocalDate();
        DriverLicense activeLicense = driverLicenses.findByDriverIdAndStatus(driverId, RecordStatus.ACTIVE)
                .orElseThrow(() -> new ConflictException("O motorista selecionado não possui CNH ativa cadastrada"));
        if (activeLicense.getExpiresOn().isBefore(expectedReturnDate)) {
            throw new ConflictException("A CNH do motorista selecionado vence antes do retorno previsto da viagem");
        }

        boolean hasOverlap = tripRequests.findDriverReservationsOverlapping(
                        driverId,
                        DRIVER_RESERVED_STATUSES,
                        departureAt,
                        expectedReturnAt
                )
                .stream()
                .anyMatch(existing -> !existing.getId().equals(excludeRequestId));
        if (hasOverlap) {
            throw new ConflictException("O motorista selecionado já possui viagem agendada sobreposta neste período");
        }
        return lockedDriver;
    }

    private Vehicle resolveEligibleRequestedVehicle(
            UUID requestedVehicleId,
            int passengerCount,
            Instant departureAt,
            Instant expectedReturnAt,
            UUID excludeRequestId
    ) {
        if (requestedVehicleId == null) {
            return null;
        }
        Vehicle vehicle = vehicles.findById(requestedVehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo", requestedVehicleId));
        maintenanceGuard.assertVehicleAvailableForAllocation(requestedVehicleId);
        if (!vehicle.supportsPassengers(passengerCount)) {
            throw new InvalidRequestException(
                    "O veículo selecionado comporta no máximo "
                            + vehicle.getAvailablePassengerSeats()
                            + " passageiro(s), insuficiente para "
                            + passengerCount
                            + " passageiro(s)"
            );
        }
        boolean hasOverlap = tripRequests.findVehicleReservationsOverlapping(
                        requestedVehicleId,
                        ACTIVE_RESERVATION_STATUSES,
                        departureAt,
                        expectedReturnAt
                )
                .stream()
                .anyMatch(existing -> !existing.getId().equals(excludeRequestId));
        if (hasOverlap) {
            throw new ConflictException("O veículo indicado já possui reserva conflitante no período informado");
        }
        return vehicle;
    }

    private static void validatePeriod(Instant departureAt, Instant expectedReturnAt) {
        if (departureAt == null || expectedReturnAt == null) {
            throw new InvalidRequestException("Data/hora de saída e retorno previsto são obrigatórias");
        }
        if (!expectedReturnAt.isAfter(departureAt)) {
            throw new InvalidRequestException("A data/hora prevista de retorno deve ser posterior à saída");
        }
    }

    private static void assertRequesterOwnership(TripRequest request, UUID requesterUserId) {
        if (!request.getRequester().getId().equals(requesterUserId)) {
            throw new UnauthorizedAccessException();
        }
    }

    private User findUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", userId));
    }

    private TripRequest lockTripRequest(UUID requestId) {
        return tripRequests.lockById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação de viagem", requestId));
    }
}
