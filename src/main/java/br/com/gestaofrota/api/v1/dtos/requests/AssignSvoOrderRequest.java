package br.com.gestaofrota.api.v1.dtos.requests;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import br.com.gestaofrota.domain.enums.SvoOrderStatus;
public record AssignSvoOrderRequest(@NotNull @PositiveOrZero Long expectedVersion, @NotNull UUID driverId, @NotNull UUID vehicleId, UUID teamId,
        @NotNull Instant scheduledAt, @NotNull Instant plannedReturnAt, Instant collectionDeadlineAt) {}
