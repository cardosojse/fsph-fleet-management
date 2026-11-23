package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.models.Fueling;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelingRepository extends JpaRepository<Fueling, UUID> {

    @Query("""
            select count(v) > 0 from Vehicle v left join v.sector s
            where v.id = :vehicleId and (:global = true or s.id = :sectorId)
            """)
    boolean existsReportVehicle(@Param("vehicleId") UUID vehicleId,
                               @Param("global") boolean global,
                               @Param("sectorId") UUID sectorId);

    @Query("""
            select f from Fueling f join fetch f.vehicle v left join v.sector s
            where f.fueledOn between :startDate and :endDate
              and (:vehicleId is null or v.id = :vehicleId)
              and (:global = true or s.id = :sectorId)
            order by f.fueledOn, v.plate, f.id
            """)
    List<Fueling> findForReport(@Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               @Param("vehicleId") UUID vehicleId,
                               @Param("global") boolean global,
                               @Param("sectorId") UUID sectorId,
                               Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "station", "registeredBy"})
    List<Fueling> findAllByOrderByFueledOnDescCreatedAtDesc();

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "station", "registeredBy"})
    List<Fueling> findByVehicleIdOrderByOdometerKmDescCreatedAtDesc(UUID vehicleId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "station", "registeredBy"})
    List<Fueling> findByRegisteredByIdOrderByFueledOnDescCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "station", "registeredBy"})
    List<Fueling> findByOutOfPatternTrueOrderByFueledOnDescCreatedAtDesc();

    @EntityGraph(attributePaths = {"vehicle", "vehicle.sector", "station", "registeredBy"})
    Optional<Fueling> findWithDetailsById(UUID id);

    Optional<Fueling> findFirstByVehicleIdOrderByOdometerKmDescCreatedAtDesc(UUID vehicleId);
}

