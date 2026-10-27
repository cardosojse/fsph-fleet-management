package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.exceptions.UnauthorizedAccessException;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.Trip;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.DriverRepository;
import br.com.gestaofrota.infra.repositories.TripRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class TripService implements OpenTripGuard {

    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final Optional<TripVehicleAuthorization> authorization;

    public TripService(
            TripRepository trips,
            VehicleRepository vehicles,
            DriverRepository drivers,
            Optional<TripVehicleAuthorization> authorization
    ) {
        this.trips = trips;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.authorization = authorization;
    }

    @Transactional
    public Trip start(UUID authenticatedUserId, UUID vehicleId, long initialOdometerKm) {
        Vehicle vehicle = lockVehicle(vehicleId);
        if (!vehicle.isActive()) {
            throw new ConflictException("Veículo inativo não pode iniciar viagem");
        }

        Driver driver = findDriver(authenticatedUserId);
        if (!driver.isActive()) {
            throw new ConflictException("Motorista inativo não pode iniciar viagem");
        }
        assertAuthorized(driver, vehicle);

        if (initialOdometerKm != vehicle.getCurrentOdometerKm()) {
            throw new InvalidRequestException("Quilometragem inicial deve ser igual à quilometragem atual do veículo");
        }
        if (trips.existsByVehicleIdAndStatus(vehicleId, TripStatus.OPEN)) {
            throw new ConflictException("Já existe uma viagem aberta para este veículo");
        }

        Trip trip = new Trip(vehicle, driver, Instant.now(), initialOdometerKm);
        try {
            return trips.saveAndFlush(trip);
        } catch (DataIntegrityViolationException exception) {
            if (isOpenVehicleUniqueConstraintViolation(exception)) {
                throw new ConflictException("Já existe uma viagem aberta para este veículo");
            }
            throw exception;
        }
    }

    @Transactional
    public Trip complete(UUID authenticatedUserId, UUID tripId, long finalOdometerKm) {
        Trip trip = trips.lockById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem", tripId));
        if (trip.getStatus() != TripStatus.OPEN) {
            throw new ConflictException("Viagem já concluída");
        }

        Driver driver = findDriver(authenticatedUserId);
        if (!Objects.equals(trip.getDriver().getId(), driver.getId())) {
            throw new UnauthorizedAccessException();
        }

        Vehicle vehicle = lockVehicle(trip.getVehicle().getId());
        assertAuthorized(driver, vehicle);

        if (finalOdometerKm < vehicle.getCurrentOdometerKm()) {
            throw new InvalidRequestException("Quilometragem final não pode ser menor que a quilometragem atual do veículo");
        }

        trip.complete(Instant.now(), finalOdometerKm);
        vehicle.updateCurrentOdometerKm(finalOdometerKm);
        return trips.saveAndFlush(trip);
    }

    /**
     * Must be called inside the caller's transaction before changing a vehicle's driver assignment.
     * Locks the vehicle before checking, matching the lock order used by {@link #start}.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertNoOpenTrip(UUID vehicleId) {
        lockVehicle(vehicleId);
        if (trips.existsByVehicleIdAndStatus(vehicleId, TripStatus.OPEN)) {
            throw new ConflictException("Não é possível alterar o vínculo enquanto houver viagem aberta");
        }
    }

    private Driver findDriver(UUID authenticatedUserId) {
        return drivers.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Motorista", authenticatedUserId));
    }

    private Vehicle lockVehicle(UUID vehicleId) {
        return vehicles.lockById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Veículo", vehicleId));
    }

    private void assertAuthorized(Driver driver, Vehicle vehicle) {
        TripVehicleAuthorization provider = authorization.orElseThrow(() ->
                new IllegalStateException("Autorização oficial de uso do veículo em viagens não está configurada"));
        if (!provider.isAuthorized(driver.getId(), vehicle.getId())) {
            throw new UnauthorizedAccessException();
        }
    }

    private boolean isOpenVehicleUniqueConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return "ux_trips_open_vehicle".equals(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
