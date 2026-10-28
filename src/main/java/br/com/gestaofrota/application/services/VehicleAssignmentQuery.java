package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.models.Vehicle;
import java.util.List;
import java.util.UUID;

public interface VehicleAssignmentQuery {
    List<Vehicle> findActiveVehiclesForDriver(UUID driverId);
}
