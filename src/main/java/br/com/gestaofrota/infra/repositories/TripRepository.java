package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.models.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    boolean existsByVehicleIdAndStatus(UUID vehicleId, TripStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trip from Trip trip where trip.id = :id")
    Optional<Trip> lockById(@Param("id") UUID id);
}
