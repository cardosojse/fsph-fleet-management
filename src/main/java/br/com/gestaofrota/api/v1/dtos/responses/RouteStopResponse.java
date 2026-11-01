package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.RouteStopStatus;
import br.com.gestaofrota.domain.models.RouteStop;
import java.util.UUID;

public record RouteStopResponse(UUID id, UUID routeId, UUID transportId, int stopSequence,
        RouteStopStatus status, String serviceType, String pickupLocation, String destination) {
    public static RouteStopResponse from(RouteStop s) {
        return new RouteStopResponse(s.getId(), s.getRoute().getId(), s.getTransport().getId(),
                s.getStopSequence(), s.getStatus(), s.getTransport().getServiceType(),
                s.getTransport().getPickupLocation(), s.getTransport().getDestination());
    }
}
