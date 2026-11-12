package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddTeamMemberRequest(
        @NotNull(message = "O ID do usuário é obrigatório")
        UUID userId,

        @NotBlank(message = "A função na equipe é obrigatória")
        @Size(max = 100, message = "A função deve ter no máximo 100 caracteres")
        String roleInTeam
) {
}
