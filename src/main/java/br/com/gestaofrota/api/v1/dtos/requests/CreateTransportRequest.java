package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateTransportRequest(
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 100) String serviceType,
        @NotBlank @Size(max = 500) String pickupLocation,
        @NotBlank @Size(max = 500) String destination
) {}
