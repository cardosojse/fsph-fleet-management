package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.RouteStatus;
import br.com.gestaofrota.domain.models.Route;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface RouteRepository extends JpaRepository<Route, UUID>, JpaSpecificationExecutor<Route> {
    @Override
    @EntityGraph(attributePaths = {"vehicle", "driver", "driver.user", "createdByUser"})
    Page<Route> findAll(Specification<Route> spec, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Route r where r.id = :id")
    Optional<Route> lockById(@Param("id") UUID id);

    boolean existsByVehicleIdAndStatus(UUID vehicleId, RouteStatus status);
    boolean existsByDriverIdAndStatus(UUID driverId, RouteStatus status);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM routes r
                WHERE r.status IN ('PLANNED', 'IN_PROGRESS')
                  AND (r.vehicle_id = :vehicleId OR r.driver_id = :driverId)
                  AND (CAST(:excludedId AS UUID) IS NULL OR r.id <> CAST(:excludedId AS UUID))
                  AND (
                    r.planned_return_at IS NULL
                    OR (
                        (CASE WHEN r.status = 'IN_PROGRESS'
                              THEN LEAST(r.planned_start_at, r.real_start_at)
                              ELSE r.planned_start_at END) < :returnAt
                        AND
                        (CASE WHEN r.status = 'IN_PROGRESS'
                              THEN GREATEST(r.planned_return_at,
                                   r.real_start_at + r.planned_duration_minutes * INTERVAL '1 minute')
                              ELSE r.planned_return_at END) > :startAt
                    )
                    OR (r.status = 'IN_PROGRESS' AND GREATEST(r.planned_return_at,
                        r.real_start_at + r.planned_duration_minutes * INTERVAL '1 minute') <= :now)
                  )
            )
            """, nativeQuery = true)
    boolean hasReservationConflict(@Param("vehicleId") UUID vehicleId, @Param("driverId") UUID driverId,
            @Param("startAt") Instant startAt, @Param("returnAt") Instant returnAt,
            @Param("excludedId") UUID excludedId, @Param("now") Instant now);
}
