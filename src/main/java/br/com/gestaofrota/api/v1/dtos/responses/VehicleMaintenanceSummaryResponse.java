package br.com.gestaofrota.api.v1.dtos.responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VehicleMaintenanceSummaryResponse(
        UUID vehicleId,
        String vehiclePlate,
        String vehicleModel,
        long currentOdometerKm,
        boolean inMaintenance,
        int totalMaintenances,
        int completedCount,
        int inProgressCount,
        int preventiveCount,
        int correctiveCount,
        BigDecimal totalAccumulatedCost,
        BigDecimal preventiveCost,
        BigDecimal correctiveCost,
        LocalDate lastMaintenanceDate,
        Long lastMaintenanceOdometerKm,
        List<MaintenanceResponse> history
) {
}

