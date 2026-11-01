package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.models.Transport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TransportRepository extends JpaRepository<Transport, UUID>, JpaSpecificationExecutor<Transport> {
    @Override
    @EntityGraph(attributePaths = {"sector", "requestedByUser"})
    Page<Transport> findAll(Specification<Transport> spec, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transport t where t.id = :id")
    Optional<Transport> lockById(@Param("id") UUID id);

    boolean existsBySectorId(UUID sectorId);
}
