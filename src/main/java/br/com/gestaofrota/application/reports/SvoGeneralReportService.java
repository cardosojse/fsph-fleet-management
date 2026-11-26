package br.com.gestaofrota.application.reports;

import br.com.gestaofrota.application.reports.SvoOperationsReportService.Period;
import br.com.gestaofrota.domain.reports.ReportData;
import br.com.gestaofrota.domain.reports.ReportHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SvoGeneralReportService {
    public static final String TITLE = "Relatório geral do SVO";
    private final FleetStatusReportService fleetStatus;
    private final SvoOperationsReportService operations;
    private final SvoFleetPanelsReportService panels;

    public SvoGeneralReportService(FleetStatusReportService fleetStatus, SvoOperationsReportService operations,
                                   SvoFleetPanelsReportService panels) {
        this.fleetStatus = fleetStatus;
        this.operations = operations;
        this.panels = panels;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result query(Period period, UUID sectorId) {
        var reference = Instant.now();
        var requests = operations.readRequests();
        var openTrips = operations.openTrips().toList();
        var openVehicles = openTrips.stream().map(trip -> trip.getVehicle().getId()).collect(Collectors.toSet());
        var fleet = fleetStatus.query(reference, openVehicles);
        var header = new ReportHeader(ReportHeader.Module.SVO, ReportHeader.Kind.GENERAL, reference);
        return new Result(header, List.of(
                new Section("availability", FleetStatusReportService.toReportData("Disponibilidade da frota", fleet, header.kind())),
                general("triage", operations.triage(reference, requests, openTrips.size()), header),
                general("daily-requests", operations.daily(reference, requests), header),
                new Section("fleet-status", FleetStatusReportService.toReportData("Frota por status", fleet, header.kind())),
                general("ongoing-trips", operations.ongoing(reference, period, sectorId, openTrips.stream()), header),
                general("vehicle-consumption", panels.consumption(reference), header),
                general("recent-fuelings", panels.recentFuelings(reference), header),
                general("maintenance-alerts", panels.maintenanceAlerts(reference), header)));
    }

    private Section general(String indicator, ReportData section, ReportHeader header) {
        return new Section(indicator, new ReportData(section.title(), section.source(), section.columns(),
                section.rows(), header, section.numericColumns()));
    }

    public record Section(String indicator, ReportData data) {}
    public record Result(ReportHeader header, List<Section> sections) {}
}
