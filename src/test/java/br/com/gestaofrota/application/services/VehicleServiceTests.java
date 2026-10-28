package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.exceptions.AssignmentProviderUnavailableException;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.Vehicle;
import br.com.gestaofrota.infra.repositories.DriverRepository;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTests {
    @Mock private VehicleRepository vehicles;
    @Mock private SectorRepository sectors;
    @Mock private DriverRepository drivers;
    @Mock private VehicleAssignmentQuery assignments;
    @Mock private Driver driver;
    @Mock private Vehicle assignedVehicle;
    @Mock private Vehicle inactiveVehicle;

    private UUID userId;
    private UUID driverId;
    private UUID assignedVehicleId;
    private VehicleService service;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        driverId = UUID.randomUUID();
        assignedVehicleId = UUID.randomUUID();
        service = new VehicleService(vehicles, sectors, drivers, Optional.of(assignments));
    }

    @Test
    void listsOnlyActiveLinkedVehiclesForDriverResolvedFromAuthenticatedUser() {
        when(drivers.findByUserId(userId)).thenReturn(Optional.of(driver));
        when(driver.isActive()).thenReturn(true);
        when(driver.getId()).thenReturn(driverId);
        when(assignedVehicle.isActive()).thenReturn(true);
        when(assignedVehicle.getId()).thenReturn(assignedVehicleId);
        when(inactiveVehicle.isActive()).thenReturn(false);
        when(assignments.findActiveVehiclesForDriver(driverId)).thenReturn(List.of(assignedVehicle, inactiveVehicle));
        when(vehicles.findAllWithSectorByIdIn(argThat(ids -> ids.equals(List.of(assignedVehicleId)))))
                .thenReturn(List.of(assignedVehicle));

        List<Vehicle> result = service.assignedToUser(userId);

        assertEquals(List.of(assignedVehicle), result);
        verify(drivers).findByUserId(userId);
        verify(assignments).findActiveVehiclesForDriver(driverId);
        verify(vehicles).findAllWithSectorByIdIn(List.of(assignedVehicleId));
    }

    @Test
    void failsExplicitlyWhenOfficialAssignmentProviderIsMissing() {
        service = new VehicleService(vehicles, sectors, drivers, Optional.empty());
        when(drivers.findByUserId(userId)).thenReturn(Optional.of(driver));
        when(driver.isActive()).thenReturn(true);
        assertThrows(AssignmentProviderUnavailableException.class, () -> service.assignedToUser(userId));
        verifyNoInteractions(assignments, vehicles);
    }
}
