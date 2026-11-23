package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.requests.FuelingReportFilterRequest;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportItemResponse;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportResponse;
import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportTotalsResponse;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure report assembly. Caller MUST supply only already-authorized records. No database access. */
@Service
public class FuelingReportService {
    private final ReportLimits limits;

    public FuelingReportService(ReportLimits limits) { this.limits = limits; }

    public FuelingReportResponse build(FuelingReportFilterRequest filters,
                                      List<FuelingReportItemResponse> authorizedRecords) {
        if (filters == null || authorizedRecords == null) {
            throw new InvalidRequestException("Filtros e registros autorizados são obrigatórios");
        }
        var selected = new ArrayList<FuelingReportItemResponse>();
        for (var item : authorizedRecords) {
            if (item == null) throw new InvalidRequestException("A lista contém abastecimento nulo");
            if (item.fueledOn().isBefore(filters.startDate()) || item.fueledOn().isAfter(filters.endDate())
                    || (filters.vehicleId() != null && !filters.vehicleId().equals(item.vehicleId()))) continue;
            // Reserve one table row for totals; never return a silently truncated report.
            if (selected.size() >= limits.maxRows() - 1) {
                throw new InvalidRequestException("O relatório excede o limite de linhas, incluindo a totalização");
            }
            selected.add(item);
        }
        selected.sort(Comparator.comparing(FuelingReportItemResponse::fueledOn)
                .thenComparing(FuelingReportItemResponse::vehiclePlate)
                .thenComparing(FuelingReportItemResponse::id));
        return new FuelingReportResponse(filters, selected, FuelingReportTotalsResponse.from(selected));
    }
}
