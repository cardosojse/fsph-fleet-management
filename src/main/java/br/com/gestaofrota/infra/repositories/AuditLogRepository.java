package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("SELECT a FROM AuditLog a " +
           "WHERE (cast(:from as timestamp) IS NULL OR a.occurredAt >= :from) " +
           "AND (cast(:to as timestamp) IS NULL OR a.occurredAt <= :to) " +
           "AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:resourceType IS NULL OR a.resourceType = :resourceType)")
    Page<AuditLog> findFiltered(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            Pageable pageable
    );
}
