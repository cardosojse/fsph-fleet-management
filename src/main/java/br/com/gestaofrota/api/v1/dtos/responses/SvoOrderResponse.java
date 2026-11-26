package br.com.gestaofrota.api.v1.dtos.responses;
import br.com.gestaofrota.domain.enums.SvoOrderStatus;
import br.com.gestaofrota.domain.models.SvoCollectionOrder;
import java.time.*;
import java.util.*;
public record SvoOrderResponse(UUID id, UUID requestId, String protocolNumber, UUID sectorId, String sectorName,
        String address, String city, String deceasedName, UUID driverId, String driverName, UUID teamId, String teamName,
        UUID vehicleId, String vehiclePlate, SvoOrderStatus status, long version, Instant requestedAt, Instant scheduledAt,
        Instant plannedReturnAt, Instant collectionDeadlineAt, Instant startedAt, Instant collectedAt, Instant deliveredAt,
        String deadlineStatus, Double requestToCollectionMinutes, Double collectionToDeliveryMinutes, Double requestToDeliveryMinutes,
        List<SvoOrderStatus> allowedTransitions, List<SvoOrderEventResponse> events) {
    public static Double minutes(Instant from, Instant to){return from==null||to==null?null:Duration.between(from,to).toMillis()/60000.0;}
    public static SvoOrderResponse from(SvoCollectionOrder o,List<SvoOrderStatus> transitions,List<SvoOrderEventResponse> events,Instant now){
        var r=o.getRequest();var d=o.getDriver();var v=o.getVehicle();var t=o.getTeam();
        String deadline=o.getCollectionDeadlineAt()==null?"UNDEFINED":o.getStatus()==SvoOrderStatus.CANCELED?"CANCELED":
            (o.getCollectedAt()==null?now:o.getCollectedAt()).isAfter(o.getCollectionDeadlineAt())?"OVERDUE":"ON_TIME";
        return new SvoOrderResponse(o.getId(),r.getId(),r.getProtocolNumber(),o.getSector().getId(),o.getSector().getName(),
            r.getAddress()+", "+r.getNeighborhood(),r.getCity(),r.getDeceasedName(),d==null?null:d.getId(),d==null?null:d.getUser().getName(),
            t==null?null:t.getId(),t==null?null:t.getName(),v==null?null:v.getId(),v==null?null:v.getPlate(),o.getStatus(),o.getVersion(),
            o.getRequestedAt(),o.getScheduledAt(),o.getPlannedReturnAt(),o.getCollectionDeadlineAt(),o.getStartedAt(),o.getCollectedAt(),o.getDeliveredAt(),
            deadline,minutes(o.getRequestedAt(),o.getCollectedAt()),minutes(o.getCollectedAt(),o.getDeliveredAt()),minutes(o.getRequestedAt(),o.getDeliveredAt()),transitions,events);
    }
}
