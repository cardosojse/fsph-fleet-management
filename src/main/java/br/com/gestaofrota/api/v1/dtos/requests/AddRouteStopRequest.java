package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record AddRouteStopRequest(@NotNull UUID transportId, @NotNull @Positive Integer stopSequence) {}
