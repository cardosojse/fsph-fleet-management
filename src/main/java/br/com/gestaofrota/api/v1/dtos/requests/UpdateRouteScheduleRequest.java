package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record UpdateRouteScheduleRequest(
        @NotNull Instant plannedStartAt,
        @Positive Integer plannedDurationMinutes,
        Instant plannedReturnAt
) {}
