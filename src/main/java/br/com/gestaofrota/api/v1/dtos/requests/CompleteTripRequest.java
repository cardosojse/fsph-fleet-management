package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CompleteTripRequest(@NotNull @PositiveOrZero Long finalOdometerKm) { }
