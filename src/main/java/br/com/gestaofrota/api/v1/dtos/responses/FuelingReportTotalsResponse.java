package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record FuelingReportTotalsResponse(int totalFuelings, BigDecimal totalLiters, BigDecimal totalValue) {
    public FuelingReportTotalsResponse {
        if (totalFuelings < 0 || totalLiters == null || totalValue == null
                || totalLiters.signum() < 0 || totalValue.signum() < 0) {
            throw new InvalidRequestException("Totais de abastecimento inválidos");
        }
        try {
            totalLiters = totalLiters.setScale(3, RoundingMode.UNNECESSARY);
            totalValue = totalValue.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidRequestException("Totais de abastecimento com precisão inválida");
        }
    }

    public static FuelingReportTotalsResponse from(List<FuelingReportItemResponse> items) {
        if (items == null) throw new InvalidRequestException("A lista de abastecimentos é obrigatória");
        BigDecimal liters = new BigDecimal("0.000");
        BigDecimal value = new BigDecimal("0.00");
        for (var item : items) {
            if (item == null) throw new InvalidRequestException("A lista contém abastecimento nulo");
            liters = liters.add(item.liters());
            value = value.add(item.totalValue());
        }
        return new FuelingReportTotalsResponse(items.size(), liters, value);
    }
}
