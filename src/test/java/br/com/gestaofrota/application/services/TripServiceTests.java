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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTests {

    @Mock private TripRepository trips;
    @Mock private VehicleRepository vehicles;
    @Mock private DriverRepository drivers;
    @Mock private TripVehicleAuthorization authorization;

    private TripService service;
    private UUID userId;
    private UUID driverId;
    private UUID vehicleId;
    private Driver driver;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new TripService(trips, vehicles, drivers, Optional.of(authorization));
        userId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        driver = mock(Driver.class);
        vehicle = mock(Vehicle.class);
    }

    @Test
    void rejectsMissingDriverForAuthenticatedUser() {
        lockActiveVehicle();
        when(drivers.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.start(userId, vehicleId, 1200));
    }

    @Test
    void rejectsInactiveDriver() {
        lockActiveVehicle();
        givenDriver(false);

        assertThrows(ConflictException.class, () -> service.start(userId, vehicleId, 1200));
        verifyNoInteractions(authorization);
    }

    @Test
    void rejectsMissingVehicle() {
        when(vehicles.lockById(vehicleId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.start(userId, vehicleId, 1200));
        verifyNoInteractions(drivers, authorization);
    }

    @Test
    void rejectsInactiveVehicle() {
        when(vehicles.lockById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicle.isActive()).thenReturn(false);

        assertThrows(ConflictException.class, () -> service.start(userId, vehicleId, 1200));
        verifyNoInteractions(drivers, authorization);
    }

    @Test
    void rejectsStartWhenAuthorizationProviderIsNotConfigured() {
        service = new TripService(trips, vehicles, drivers, Optional.empty());
        lockActiveVehicle();
        givenDriver(true);

        assertThrows(IllegalStateException.class, () -> service.start(userId, vehicleId, 1200));
        verifyNoInteractions(trips);
    }

    @Test
    void rejectsStartWhenAuthorizationIsDenied() {
        lockActiveVehicle();
        givenDriver(true);
        givenDriverIdentity();
        givenVehicleIdentity();
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class, () -> service.start(userId, vehicleId, 1200));
        verifyNoInteractions(trips);
    }

    @Test
    void rejectsInitialOdometerDifferentFromCurrentVehicleReading() {
        lockActiveVehicle();
        givenDriver(true);
        givenDriverIdentity();
        givenVehicleIdentity();
        givenVehicleOdometer(1200);
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(true);

        assertThrows(InvalidRequestException.class, () -> service.start(userId, vehicleId, 1199));
        verifyNoInteractions(trips);
    }

    @Test
    void rejectsSecondOpenTripForVehicle() {
        lockActiveVehicle();
        givenDriver(true);
        givenDriverIdentity();
        givenVehicleIdentity();
        givenVehicleOdometer(1200);
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(true);
        when(trips.existsByVehicleIdAndStatus(vehicleId, TripStatus.OPEN)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.start(userId, vehicleId, 1200));
        verify(trips, never()).saveAndFlush(any());
    }

    @Test
    void translatesDatabaseOpenTripConstraintViolationToConflict() {
        lockActiveVehicle();
        givenDriver(true);
        givenDriverIdentity();
        givenVehicleIdentity();
        givenVehicleOdometer(1200);
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(true);
        when(trips.saveAndFlush(any(Trip.class))).thenThrow(new DataIntegrityViolationException(
                "unique index", new org.hibernate.exception.ConstraintViolationException(
                "unique index", new SQLException(), "ux_trips_open_vehicle")));

        assertThrows(ConflictException.class, () -> service.start(userId, vehicleId, 1200));
    }

    @Test
    void startsTripForDriverResolvedFromAuthenticatedUser() {
        lockActiveVehicle();
        givenDriver(true);
        givenDriverIdentity();
        givenVehicleIdentity();
        givenVehicleOdometer(1200);
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(true);
        when(trips.saveAndFlush(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Trip started = service.start(userId, vehicleId, 1200);

        assertAll(
                () -> assertSame(vehicle, started.getVehicle()),
                () -> assertSame(driver, started.getDriver()),
                () -> assertEquals(1200L, started.getInitialOdometerKm()),
                () -> assertEquals(TripStatus.OPEN, started.getStatus())
        );
        verify(drivers).findByUserId(userId);
        verify(trips).saveAndFlush(any(Trip.class));
    }

    @Test
    void rejectsCompletionWhenTripDoesNotExist() {
        UUID tripId = UUID.randomUUID();
        when(trips.lockById(tripId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.complete(userId, tripId, 1300));
        verifyNoInteractions(drivers, vehicles, authorization);
    }

    @Test
    void rejectsCompletionOfAlreadyCompletedTrip() {
        Trip trip = openTrip();
        trip.complete(Instant.now(), 1300);
        when(trips.lockById(any())).thenReturn(Optional.of(trip));

        assertThrows(ConflictException.class, () -> service.complete(userId, UUID.randomUUID(), 1400));
        verifyNoInteractions(drivers, vehicles, authorization);
    }

    @Test
    void rejectsCompletionByDifferentDriver() {
        givenDriverIdentity();
        Trip trip = openTrip();
        Driver anotherDriver = driverFor(userId, UUID.randomUUID());
        when(trips.lockById(any())).thenReturn(Optional.of(trip));
        when(drivers.findByUserId(userId)).thenReturn(Optional.of(anotherDriver));

        assertThrows(UnauthorizedAccessException.class, () -> service.complete(userId, UUID.randomUUID(), 1300));
        verifyNoInteractions(vehicles, authorization);
    }

    @Test
    void rejectsCompletionWhenAuthorizationIsDenied() {
        Trip trip = openTrip();
        when(trips.lockById(any())).thenReturn(Optional.of(trip));
        givenAuthenticatedDriver();
        givenDriverIdentity();
        lockVehicle();
        givenVehicleIdentity();
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(false);

        assertThrows(UnauthorizedAccessException.class, () -> service.complete(userId, UUID.randomUUID(), 1300));
        verify(vehicle, never()).updateCurrentOdometerKm(anyLong());
    }

    @Test
    void rejectsFinalOdometerBelowVehicleCurrentReading() {
        Trip trip = openTrip();
        when(trips.lockById(any())).thenReturn(Optional.of(trip));
        givenAuthenticatedDriver();
        givenDriverIdentity();
        lockVehicle();
        givenVehicleIdentity();
        givenVehicleOdometer(1200);
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(true);

        assertThrows(InvalidRequestException.class, () -> service.complete(userId, UUID.randomUUID(), 1199));
        verify(vehicle, never()).updateCurrentOdometerKm(anyLong());
        assertEquals(TripStatus.OPEN, trip.getStatus());
    }

    @Test
    void rejectsFinalOdometerBelowTripInitialReading() {
        Trip trip = new Trip(vehicle, driver, Instant.now().minusSeconds(30), 1200);
        when(trips.lockById(any())).thenReturn(Optional.of(trip));
        givenAuthenticatedDriver();
        givenDriverIdentity();
        lockVehicle();
        givenVehicleIdentity();
        givenVehicleOdometer(1100);
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(true);

        assertThrows(InvalidRequestException.class, () -> service.complete(userId, UUID.randomUUID(), 1199));
        verify(vehicle, never()).updateCurrentOdometerKm(anyLong());
        assertEquals(TripStatus.OPEN, trip.getStatus());
    }

    @Test
    void completesTripAndUpdatesVehicleOdometer() {
        Trip trip = openTrip();
        when(trips.lockById(any())).thenReturn(Optional.of(trip));
        givenAuthenticatedDriver();
        givenDriverIdentity();
        lockVehicle();
        givenVehicleIdentity();
        givenVehicleOdometer(1200);
        when(authorization.isAuthorized(driverId, vehicleId)).thenReturn(true);
        when(trips.saveAndFlush(trip)).thenReturn(trip);

        Trip completed = service.complete(userId, UUID.randomUUID(), 1300);

        assertEquals(TripStatus.COMPLETED, completed.getStatus());
        assertEquals(1300L, completed.getFinalOdometerKm());
        verify(vehicle).updateCurrentOdometerKm(1300L);
        verify(trips).saveAndFlush(trip);
    }

    @Test
    void openTripGuardLocksVehicleAndRejectsAssignmentChangeDuringOpenTrip() {
        lockVehicle();
        when(trips.existsByVehicleIdAndStatus(vehicleId, TripStatus.OPEN)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.assertNoOpenTrip(vehicleId));

        verify(vehicles, times(1)).lockById(vehicleId);
    }

    private void lockActiveVehicle() {
        lockVehicle();
        when(vehicle.isActive()).thenReturn(true);
    }

    private void lockVehicle() {
        when(vehicles.lockById(vehicleId)).thenReturn(Optional.of(vehicle));
    }

    private void givenVehicleIdentity() {
        when(vehicle.getId()).thenReturn(vehicleId);
    }

    private void givenVehicleOdometer(long odometerKm) {
        when(vehicle.getCurrentOdometerKm()).thenReturn(odometerKm);
    }

    private void givenDriver(boolean active) {
        givenAuthenticatedDriver();
        when(driver.isActive()).thenReturn(active);
    }

    private void givenAuthenticatedDriver() {
        when(drivers.findByUserId(userId)).thenReturn(Optional.of(driver));
    }

    private void givenDriverIdentity() {
        when(driver.getId()).thenReturn(driverId);
    }

    private Trip openTrip() {
        return new Trip(vehicle, driver, Instant.now().minusSeconds(30), 1200);
    }

    private Driver driverFor(UUID authenticatedUserId, UUID anotherDriverId) {
        Driver anotherDriver = mock(Driver.class);
        when(anotherDriver.getId()).thenReturn(anotherDriverId);
        return anotherDriver;
    }
}
