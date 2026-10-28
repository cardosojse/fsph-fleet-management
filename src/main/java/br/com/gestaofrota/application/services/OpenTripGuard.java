package br.com.gestaofrota.application.services;

import java.util.UUID;

/**
 * Shared transaction contract for the official assignment mutation flow. Assignment changes must
 * call this guard before ending/replacing an assignment, in the same transaction, and hold the
 * vehicle lock through commit so trip start and assignment change serialize on the same vehicle.
 */
public interface OpenTripGuard {

    void assertNoOpenTrip(UUID vehicleId);
}
