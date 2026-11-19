package br.com.gestaofrota.api.v1.dtos.responses;

import java.time.Instant;
import java.util.List;

public record SvoCoverageReportResponse(
        Instant periodStart,
        Instant periodEnd,
        boolean fullyCovered,
        int totalGaps,
        long totalUncoveredMinutes,
        double totalUncoveredHours,
        List<SvoCoverageGapResponse> gaps
) {
}
