package br.com.gestaofrota.application.services;

import java.util.UUID;

/** Shared transaction contract for code that changes a vehicle's driver assignment. */
public interface OpenTripGuard {

    void assertNoOpenTrip(UUID vehicleId);
}
