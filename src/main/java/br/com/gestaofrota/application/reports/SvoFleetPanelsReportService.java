package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.api.v1.dtos.responses.PreventiveScheduleResponse;
import br.com.gestaofrota.application.services.FuelingService;
import br.com.gestaofrota.application.services.MaintenanceService;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportData.NumberFormat;
import br.com.gestaofrota.domain.reports.ReportHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class SvoFleetPanelsReportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final String MISSING = "—";

    private final FuelingService fuelings;
    private final MaintenanceService maintenance;

    public SvoFleetPanelsReportService(FuelingService fuelings, MaintenanceService maintenance) {
        this.fuelings = fuelings;
        this.maintenance = maintenance;
    }

    public ReportData consumption() {
        return consumption(Instant.now());
    }

    ReportData consumption(Instant reference) {
        var rows = fuelings.compareFleetConsumption().vehicles().stream().limit(5)
                .map(item -> List.of(vehicleLabel(item.vehiclePlate(), item.vehicleModel()),
                        Long.toString(item.totalMeasuredDistanceKm()), decimal(item.averageKmPerLiter())))
                .toList();
        return report("Consumo por veículo", reference, List.of("Veículo", "Km medidos", "Consumo médio (km/L)"),
                rows, Map.of(1, NumberFormat.INTEGER, 2, NumberFormat.DECIMAL_2));
    }

    public ReportData recentFuelings() {
        return recentFuelings(Instant.now());
    }

    ReportData recentFuelings(Instant reference) {
        // Stable sort by day retains the operational order for records from the same day.
        var rows = fuelings.list(null, null, null, null).stream()
                .sorted(Comparator.comparing(fueling -> fueling.getFueledOn(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(item -> List.of(vehicleLabel(item.getVehicle().getPlate(), item.getVehicle().getModel()),
                        date(item.getFueledOn()), decimal(item.getLiters()), decimal(item.getTotalValue())))
                .toList();
        return report("Abastecimentos recentes", reference, List.of("Veículo", "Data", "Litros", "Valor total"),
                rows, Map.of(2, NumberFormat.DECIMAL_2, 3, NumberFormat.BRL));
    }

    public ReportData maintenanceAlerts() {
        return maintenanceAlerts(Instant.now());
    }

    ReportData maintenanceAlerts(Instant reference) {
        var rows = maintenance.listActiveAlertsAt(reference).stream().map(SvoFleetPanelsReportService::alertRow).toList();
        return report("Alertas de manutenção", reference, List.of("Alerta", "Detalhes"), rows, Map.of());
    }

    static List<String> alertRow(PreventiveScheduleResponse alert) {
        var title = new ArrayList<String>();
        if (hasText(alert.vehiclePlate())) title.add(alert.vehiclePlate());
        if (hasText(alert.serviceName())) title.add(alert.serviceName());

        var details = new ArrayList<String>();
        if (alert.targetDate() != null) details.add("Previsão: " + date(alert.targetDate()));
        if (alert.targetOdometerKm() != null) {
            var km = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("pt-BR"))
                    .format(alert.targetOdometerKm());
            details.add("Odômetro previsto: " + km + " km");
        }
        if (hasText(alert.notes())) details.add(alert.notes());
        return List.of(title.isEmpty() ? "Alerta de manutenção" : String.join(" — ", title),
                details.isEmpty() ? "Consulte o plano de manutenção." : String.join(" • ", details));
    }

    private static boolean hasText(String value) { return value != null && !value.isEmpty(); }

    private static String vehicleLabel(String plate, String model) {
        return plate != null ? plate : model != null ? model : "Veículo";
    }

    private static String date(LocalDate value) { return value == null ? MISSING : DATE.format(value); }

    private static String decimal(BigDecimal value) {
        return value == null ? MISSING : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static ReportData report(String title, Instant reference, List<String> columns, List<List<String>> rows,
                                     Map<Integer, NumberFormat> numericColumns) {
        return new ReportData(title, "Situação atual", columns, rows,
                new ReportHeader(ReportHeader.Module.SVO, ReportHeader.Kind.INDIVIDUAL, reference), numericColumns);
    }
}
