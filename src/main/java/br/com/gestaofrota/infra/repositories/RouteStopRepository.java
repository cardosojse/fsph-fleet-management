package br.com.gestaofrota.infra.repositories;

import br.com.gestaofrota.domain.enums.RouteStopStatus;
import br.com.gestaofrota.domain.models.RouteStop;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {
    @EntityGraph(attributePaths = "transport")
    List<RouteStop> findByRouteIdOrderByStopSequence(UUID routeId);
    Optional<RouteStop> findByIdAndRouteId(UUID id, UUID routeId);
    List<RouteStop> findByRouteIdAndStatusOrderByTransportId(UUID routeId, RouteStopStatus status);
    Optional<RouteStop> findFirstByRouteIdAndStatusOrderByStopSequence(UUID routeId, RouteStopStatus status);
    boolean existsByRouteIdAndStopSequence(UUID routeId, int stopSequence);
    boolean existsByRouteIdAndStatus(UUID routeId, RouteStopStatus status);
    boolean existsByTransportIdAndStatus(UUID transportId, RouteStopStatus status);
}
