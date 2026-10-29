package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.MaintenanceScheduleStatus;
import br.com.gestaofrota.domain.models.PreventiveMaintenanceSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreventiveMaintenanceScheduleRepository extends JpaRepository<PreventiveMaintenanceSchedule, UUID> {

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "completedMaintenance"})
    List<PreventiveMaintenanceSchedule> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "completedMaintenance"})
    List<PreventiveMaintenanceSchedule> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "completedMaintenance"})
    List<PreventiveMaintenanceSchedule> findByStatusOrderByTargetDateAsc(MaintenanceScheduleStatus status);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "completedMaintenance"})
    Optional<PreventiveMaintenanceSchedule> findWithVehicleById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from PreventiveMaintenanceSchedule schedule join fetch schedule.vehicle where schedule.id = :id")
    Optional<PreventiveMaintenanceSchedule> lockById(@Param("id") UUID id);
}
