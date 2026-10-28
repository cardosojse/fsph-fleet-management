package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.Trip;
import org.springframework.data.domain.Page;
import java.util.List;

public record TripHistoryResponse(List<TripResponse> content, int page, int size,
                                 long totalElements, int totalPages) {
    public static TripHistoryResponse from(Page<Trip> trips) {
        return new TripHistoryResponse(trips.getContent().stream().map(TripResponse::from).toList(),
                trips.getNumber(), trips.getSize(), trips.getTotalElements(), trips.getTotalPages());
    }
}
