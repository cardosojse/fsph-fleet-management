package br.com.gestaofrota.application.services;

import java.util.UUID;

public interface MaintenanceAvailabilityGuard {

    void assertVehicleAvailableForAllocation(UUID vehicleId);

    boolean isVehicleInMaintenance(UUID vehicleId);
}

