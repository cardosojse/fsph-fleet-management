package br.com.gestaofrota.api.v1.dtos.responses;
import br.com.gestaofrota.domain.enums.SvoOrderStatus;
import br.com.gestaofrota.domain.models.SvoOrderEvent;
import java.time.Instant;
import java.util.UUID;
public record SvoOrderEventResponse(UUID id, UUID authorId, String authorName, String type, String text,
        SvoOrderStatus fromStatus, SvoOrderStatus toStatus, Instant occurredAt, long orderVersion) {
    public static SvoOrderEventResponse from(SvoOrderEvent e){return new SvoOrderEventResponse(e.getId(),e.getAuthor().getId(),e.getAuthorName(),e.getType(),e.getText(),e.getFromStatus(),e.getToStatus(),e.getOccurredAt(),e.getOrderVersion());}
}
