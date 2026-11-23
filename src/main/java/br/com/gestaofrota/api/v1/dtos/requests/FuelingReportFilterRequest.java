package br.com.gestaofrota.api.v1.dtos.requests;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import java.time.LocalDate;
import java.util.UUID;

/** Selection criteria only; vehicleId is not proof of permission to access a vehicle. */
public record FuelingReportFilterRequest(LocalDate startDate, LocalDate endDate, UUID vehicleId) {
    public FuelingReportFilterRequest {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new InvalidRequestException("Informe um período válido com datas inicial e final");
        }
    }
}
