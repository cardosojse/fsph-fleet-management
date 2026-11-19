package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.FuelCardStatus;
import br.com.gestaofrota.domain.models.FuelCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FuelCardRepository extends JpaRepository<FuelCard, UUID> {

    Optional<FuelCard> findByCardNumberIgnoreCase(String cardNumber);

    boolean existsByCardNumberIgnoreCase(String cardNumber);

    Optional<FuelCard> findByVehicleId(UUID vehicleId);

    @Query("SELECT c FROM FuelCard c " +
            "WHERE (:status IS NULL OR c.status = :status) " +
            "AND (:vehicleId IS NULL OR (c.vehicle IS NOT NULL AND c.vehicle.id = :vehicleId)) " +
            "AND (:search IS NULL OR LOWER(c.cardNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(c.cardholderName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<FuelCard> findFiltered(
            @Param("status") FuelCardStatus status,
            @Param("vehicleId") UUID vehicleId,
            @Param("search") String search,
            Pageable pageable
    );
}
