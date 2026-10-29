package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.MaintenanceStatus;
import br.com.gestaofrota.domain.models.VehicleMaintenance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleMaintenanceRepository extends JpaRepository<VehicleMaintenance, UUID> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector"})
    List<VehicleMaintenance> findAllByOrderByPerformedOnDescCreatedAtDesc();

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector"})
    List<VehicleMaintenance> findByVehicleIdOrderByPerformedOnDescCreatedAtDesc(UUID vehicleId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector"})
    Optional<VehicleMaintenance> findWithVehicleById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select maintenance from VehicleMaintenance maintenance join fetch maintenance.vehicle where maintenance.id = :id")
    Optional<VehicleMaintenance> lockById(@Param("id") UUID id);

    boolean existsByVehicleIdAndStatus(UUID vehicleId, MaintenanceStatus status);
}
