package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Driver;
import br.com.gestaofrota.domain.models.Sector;
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

public interface DriverRepository
        extends JpaRepository<Driver, UUID> {

    @Override
    @EntityGraph(attributePaths = {"user", "user.sector"})
    List<Driver> findAll(Sort sort);

    @Override
    @EntityGraph(attributePaths = {"user", "user.sector"})
    Optional<Driver> findById(UUID id);

    boolean existsByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select driver from Driver driver where driver.id = :id")
    Optional<Driver> lockById(@Param("id") UUID id);

    List<Driver> findByActive(boolean active);

    List<Driver> findByUser_Role(UserRole role);

    List<Driver> findByUser_Sector(Sector sector);

}