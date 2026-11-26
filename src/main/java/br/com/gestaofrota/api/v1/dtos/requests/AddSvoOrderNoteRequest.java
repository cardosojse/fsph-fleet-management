package br.com.gestaofrota.api.v1.dtos.requests;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import br.com.gestaofrota.domain.enums.SvoOrderStatus;
public record AddSvoOrderNoteRequest(@NotNull @PositiveOrZero Long expectedVersion, @NotBlank @Size(max=2000) String text) {}
