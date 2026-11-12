package br.com.gestaofrota.api.v1.dtos.responses;

import java.time.Instant;

public record SvoCoverageGapResponse(
        Instant gapStart,
        Instant gapEnd,
        long durationMinutes,
        double durationHours
) {
}
