package br.com.gestaofrota.application.services;

import br.com.gestaofrota.domain.models.Vehicle;
import java.util.List;
import java.util.UUID;

/**
 * Query port for the official assignment source. Implementations must scope results to the supplied
 * driver and return only vehicles that are active and actively assigned at query time.
 */
public interface VehicleAssignmentQuery {
    List<Vehicle> findActiveVehiclesForDriver(UUID driverId);
}
