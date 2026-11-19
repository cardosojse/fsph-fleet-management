package br.com.gestaofrota.api.v1.dtos.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSvoTeamRequest(
        @NotBlank(message = "O nome da equipe é obrigatório")
        @Size(max = 150, message = "O nome da equipe deve ter no máximo 150 caracteres")
        String name,

        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String description,

        List<@Valid AddTeamMemberRequest> initialMembers
) {
}
