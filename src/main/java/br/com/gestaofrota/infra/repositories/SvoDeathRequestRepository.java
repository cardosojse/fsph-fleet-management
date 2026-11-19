package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.SvoRequestStatus;
import br.com.gestaofrota.domain.models.SvoDeathRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SvoDeathRequestRepository extends JpaRepository<SvoDeathRequest, UUID> {

    Optional<SvoDeathRequest> findByProtocolNumberIgnoreCase(String protocolNumber);

    boolean existsByProtocolNumberIgnoreCase(String protocolNumber);

    Page<SvoDeathRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId, Pageable pageable);

    long countByCreatedAtBetween(Instant from, Instant to);

    @Query("SELECT r FROM SvoDeathRequest r " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "AND (:city IS NULL OR LOWER(r.city) = LOWER(:city)) " +
            "AND (cast(:from as timestamp) IS NULL OR r.createdAt >= :from) " +
            "AND (cast(:to as timestamp) IS NULL OR r.createdAt <= :to) " +
            "AND (:search IS NULL OR LOWER(r.protocolNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(r.deceasedName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(r.contactName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(r.address) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<SvoDeathRequest> findFiltered(
            @Param("status") SvoRequestStatus status,
            @Param("city") String city,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("search") String search,
            Pageable pageable
    );
}
