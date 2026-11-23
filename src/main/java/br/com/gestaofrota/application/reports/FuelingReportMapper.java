package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.responses.FuelingReportResponse;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportFormat;
import br.com.gestaofrota.domain.reports.ReportHeader;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FuelingReportMapper {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final List<String> COLUMNS = List.of(
            "Data", "Placa", "Modelo", "Combustível", "Litros", "Preço/litro", "Valor");
    private final ReportLimits limits;
    private final ReportValidator validator;

    public FuelingReportMapper(ReportLimits limits, ReportValidator validator) {
        this.limits = limits;
        this.validator = validator;
    }

    public ReportData toReportData(FuelingReportResponse report, ReportFormat format) {
        if (report == null || format == null) {
            throw new InvalidRequestException("Relatório e formato são obrigatórios");
        }
        if ((long) report.items().size() + 1 > limits.maxRows()
                || COLUMNS.size() > limits.maxColumns()) {
            throw new InvalidRequestException("O relatório excede o limite de linhas ou colunas de exportação");
        }
        var rows = new ArrayList<List<String>>();
        for (var item : report.items()) {
            rows.add(List.of(DATE.format(item.fueledOn()), item.vehiclePlate(), item.vehicleModel(),
                    item.fuelType().name(), decimal(item.liters()), decimal(item.getPricePerLiter()),
                    decimal(item.totalValue())));
        }
        rows.add(List.of("TOTAL", "", "", "", decimal(report.totals().totalLiters()), "",
                decimal(report.totals().totalValue())));
        var filters = report.filters();
        String source = "Período: " + DATE.format(filters.startDate()) + " a " + DATE.format(filters.endDate())
                + "; veículo: " + (filters.vehicleId() == null ? "não especificado" : filters.vehicleId())
                + "; registros: " + report.totals().totalFuelings();
        if (report.items().isEmpty()) source += "; sem registros";
        var data = new ReportData("Abastecimentos", source, COLUMNS, rows,
                new ReportHeader(ReportHeader.Module.FLEET, ReportHeader.Kind.INDIVIDUAL, report.referenceAt()),
                Map.of(4, ReportData.NumberFormat.DECIMAL_3, 5, ReportData.NumberFormat.BRL,
                        6, ReportData.NumberFormat.BRL));
        validator.validate(data, format);
        return data;
    }

    private String decimal(BigDecimal value) { return value.toPlainString().replace('.', ','); }
}
