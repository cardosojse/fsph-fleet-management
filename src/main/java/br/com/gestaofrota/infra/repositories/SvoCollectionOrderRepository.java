package br.com.gestaofrota.infra.repositories;
import br.com.gestaofrota.domain.models.SvoCollectionOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SvoCollectionOrderRepository extends JpaRepository<SvoCollectionOrder,UUID>, JpaSpecificationExecutor<SvoCollectionOrder> {
    Optional<SvoCollectionOrder> findByRequestId(UUID requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from SvoCollectionOrder o where o.id=:id")
    Optional<SvoCollectionOrder> lockById(@Param("id") UUID id);
}
