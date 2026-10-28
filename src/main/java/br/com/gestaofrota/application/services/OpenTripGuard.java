package br.com.gestaofrota.application.services;

import java.util.UUID;

public interface OpenTripGuard {

    void assertNoOpenTrip(UUID vehicleId);
}
