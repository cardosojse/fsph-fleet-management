package br.com.gestaofrota.application.services;

import java.util.UUID;

/**
 * Authorization port implemented by the official assignment feature. It must check the current
 * active driver-vehicle assignment, using that feature's authoritative source of truth.
 */
public interface TripVehicleAuthorization {

    boolean isAuthorized(UUID driverId, UUID vehicleId);
}
