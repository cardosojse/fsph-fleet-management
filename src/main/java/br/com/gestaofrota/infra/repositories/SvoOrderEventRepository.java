package br.com.gestaofrota.infra.repositories;
import br.com.gestaofrota.domain.models.SvoOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SvoOrderEventRepository extends JpaRepository<SvoOrderEvent,UUID> {
    List<SvoOrderEvent> findByOrderIdOrderByOrderVersionAscOccurredAtAsc(UUID orderId);
}
