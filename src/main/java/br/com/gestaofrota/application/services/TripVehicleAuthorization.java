package br.com.gestaofrota.application.services;

import java.util.UUID;

public interface TripVehicleAuthorization {

    boolean isAuthorized(UUID driverId, UUID vehicleId);
}
