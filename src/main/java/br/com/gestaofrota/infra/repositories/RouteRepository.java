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

public interface RouteRepository extends JpaRepository<Route, UUID>, JpaSpecificationExecutor<Route> {
    @Override
    @EntityGraph(attributePaths = {"vehicle", "driver", "driver.user", "createdByUser"})
    Page<Route> findAll(Specification<Route> spec, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Route r where r.id = :id")
    Optional<Route> lockById(@Param("id") UUID id);

    boolean existsByVehicleIdAndStatus(UUID vehicleId, RouteStatus status);
    boolean existsByDriverIdAndStatus(UUID driverId, RouteStatus status);
}
