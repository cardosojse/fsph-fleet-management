package br.com.gestaofrota.application.services;

import java.util.UUID;

/** Implemented by the official driver-vehicle assignment feature. */
public interface TripVehicleAuthorization {

    boolean isAuthorized(UUID driverId, UUID vehicleId);
}
