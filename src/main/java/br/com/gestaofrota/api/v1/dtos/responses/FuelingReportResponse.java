package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.api.v1.dtos.requests.FuelingReportFilterRequest;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.reports.ReportHeader;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record FuelingReportResponse(
        FuelingReportFilterRequest filters,
        List<FuelingReportItemResponse> items,
        FuelingReportTotalsResponse totals,
        Instant referenceAt
) {
    public FuelingReportResponse(FuelingReportFilterRequest filters, List<FuelingReportItemResponse> items,
                                 FuelingReportTotalsResponse totals) {
        this(filters, items, totals, Instant.now());
    }

    @com.fasterxml.jackson.annotation.JsonProperty("referenceLabel")
    public String referenceLabel() {
        return new ReportHeader(ReportHeader.Module.FLEET, ReportHeader.Kind.INDIVIDUAL, referenceAt)
                .referenceLabel();
    }

    public FuelingReportResponse {
        if (filters == null || items == null || totals == null) {
            throw new InvalidRequestException("Filtros, registros e totais do relatório são obrigatórios");
        }
        var ids = new HashSet<UUID>();
        for (var item : items) {
            if (item == null || !ids.add(item.id()) || item.fueledOn().isBefore(filters.startDate())
                    || item.fueledOn().isAfter(filters.endDate())
                    || (filters.vehicleId() != null && !filters.vehicleId().equals(item.vehicleId()))) {
                throw new InvalidRequestException("Relatório contém registro nulo, duplicado ou fora dos filtros");
            }
        }
        var expected = FuelingReportTotalsResponse.from(items);
        if (totals.totalFuelings() != expected.totalFuelings()
                || totals.totalLiters().compareTo(expected.totalLiters()) != 0
                || totals.totalValue().compareTo(expected.totalValue()) != 0) {
            throw new InvalidRequestException("Os totais não correspondem aos registros do relatório");
        }
        items = List.copyOf(items);
    }
}
