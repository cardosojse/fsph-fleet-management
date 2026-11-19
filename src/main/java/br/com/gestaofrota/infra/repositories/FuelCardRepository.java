package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.FuelCardStatus;
import br.com.gestaofrota.domain.models.FuelCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FuelCardRepository extends JpaRepository<FuelCard, UUID> {

    @Override
    @EntityGraph(attributePaths = "vehicle")
    Optional<FuelCard> findById(UUID id);

    @EntityGraph(attributePaths = "vehicle")
    Optional<FuelCard> findByCardNumberIgnoreCase(String cardNumber);

    boolean existsByCardNumberIgnoreCase(String cardNumber);

    @EntityGraph(attributePaths = "vehicle")
    Optional<FuelCard> findByVehicleId(UUID vehicleId);

    @EntityGraph(attributePaths = "vehicle")
    @Query("SELECT c FROM FuelCard c " +
            "WHERE (:status IS NULL OR c.status = :status) " +
            "AND (:vehicleId IS NULL OR (c.vehicle IS NOT NULL AND c.vehicle.id = :vehicleId)) " +
            "AND (cast(:search as string) IS NULL OR LOWER(c.cardNumber) LIKE LOWER(CONCAT('%', cast(:search as string), '%')) " +
            "     OR LOWER(c.cardholderName) LIKE LOWER(CONCAT('%', cast(:search as string), '%')))")
    Page<FuelCard> findFiltered(
            @Param("status") FuelCardStatus status,
            @Param("vehicleId") UUID vehicleId,
            @Param("search") String search,
            Pageable pageable
    );
}
