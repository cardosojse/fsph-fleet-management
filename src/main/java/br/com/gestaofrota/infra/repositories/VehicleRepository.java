package br.com.gestaofrota.infra.repositories;


import br.com.gestaofrota.domain.models.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByPlate(String plate);

    List<Vehicle> findByCapacity (int capacity);


    @Override
    @EntityGraph(attributePaths = "sector")
    List<Vehicle> findAll(Sort sort);

    @Override
    @EntityGraph(attributePaths = "sector")
    Optional<Vehicle> findById(UUID id);

    @EntityGraph(attributePaths = "sector")
    List<Vehicle> findByActiveTrueAndInMaintenanceFalseOrderByPlateAsc();

    boolean existsByPlateIgnoreCase(String plate);
    boolean existsByPlateIgnoreCaseAndIdNot(String plate, UUID id);
    boolean existsBySectorId(UUID sectorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vehicle from Vehicle vehicle where vehicle.id = :id")
    Optional<Vehicle> lockById(@Param("id") UUID id);

}
