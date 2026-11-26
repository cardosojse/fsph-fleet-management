package br.com.gestaofrota.api.v1.dtos.requests;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import br.com.gestaofrota.domain.enums.SvoOrderStatus;
public record TransitionSvoOrderRequest(@NotNull @PositiveOrZero Long expectedVersion, @NotNull SvoOrderStatus status, @Size(max=2000) String observation) {}
