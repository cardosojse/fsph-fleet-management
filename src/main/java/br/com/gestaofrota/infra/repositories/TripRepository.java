package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.TripStatus;
import br.com.gestaofrota.domain.models.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    interface CompletedTripInterval {
        Instant getStartedAt();
        Instant getEndedAt();
    }

    boolean existsByVehicleIdAndStatus(UUID vehicleId, TripStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trip from Trip trip where trip.id = :id")
    Optional<Trip> lockById(@Param("id") UUID id);

    @Query(value = "select trip from Trip trip join fetch trip.vehicle join fetch trip.driver " +
            "where (:vehicleId is null or trip.vehicle.id = :vehicleId) " +
            "and (:driverId is null or trip.driver.id = :driverId) " +
            "and (:startedFrom is null or trip.startedAt >= :startedFrom) " +
            "and (:startedTo is null or trip.startedAt <= :startedTo)",
            countQuery = "select count(trip) from Trip trip " +
                    "where (:vehicleId is null or trip.vehicle.id = :vehicleId) " +
                    "and (:driverId is null or trip.driver.id = :driverId) " +
                    "and (:startedFrom is null or trip.startedAt >= :startedFrom) " +
                    "and (:startedTo is null or trip.startedAt <= :startedTo)")
    Page<Trip> findHistory(@Param("vehicleId") UUID vehicleId, @Param("driverId") UUID driverId,
                           @Param("startedFrom") Instant startedFrom, @Param("startedTo") Instant startedTo,
                           Pageable pageable);

    @Query("select trip.startedAt as startedAt, trip.endedAt as endedAt from Trip trip " +
            "where trip.vehicle.id = :vehicleId and trip.status = br.com.gestaofrota.domain.enums.TripStatus.COMPLETED " +
            "and trip.startedAt < :periodEndExclusive and trip.endedAt > :periodStart")
    List<CompletedTripInterval> findCompletedIntervalsOverlappingPeriod(
            @Param("vehicleId") UUID vehicleId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEndExclusive") Instant periodEndExclusive
    );
}
