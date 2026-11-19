package br.com.gestaofrota.api.v1.dtos.requests;

import br.com.gestaofrota.domain.enums.DeathLocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateSvoDeathRequest(
        @Size(max = 150, message = "Nome do falecido não pode exceder 150 caracteres")
        String deceasedName,

        Integer deceasedAge,

        @Size(max = 20, message = "Gênero não pode exceder 20 caracteres")
        String deceasedGender,

        @NotNull(message = "Data e hora aproximada do óbito são obrigatórias")
        Instant occurredAt,

        @NotNull(message = "Tipo de local da ocorrência é obrigatório")
        DeathLocationType deathLocationType,

        @NotBlank(message = "Endereço da ocorrência é obrigatório")
        @Size(max = 250, message = "Endereço não pode exceder 250 caracteres")
        String address,

        @NotBlank(message = "Bairro da ocorrência é obrigatório")
        @Size(max = 100, message = "Bairro não pode exceder 100 caracteres")
        String neighborhood,

        @NotBlank(message = "Cidade da ocorrência é obrigatória")
        @Size(max = 100, message = "Cidade não pode exceder 100 caracteres")
        String city,

        @Size(max = 250, message = "Ponto de referência não pode exceder 250 caracteres")
        String referencePoint,

        @NotBlank(message = "Nome do declarante/contato é obrigatório")
        @Size(max = 150, message = "Nome do declarante não pode exceder 150 caracteres")
        String contactName,

        @NotBlank(message = "Telefone do contato é obrigatório")
        @Size(max = 30, message = "Telefone não pode exceder 30 caracteres")
        String contactPhone,

        @Size(max = 100, message = "Vínculo com o falecido não pode exceder 100 caracteres")
        String relationshipWithDeceased,

        @Size(max = 500, message = "Circunstância presumida não pode exceder 500 caracteres")
        String presumedCircumstance,

        Boolean hasSuspectedViolence,

        @Size(max = 500, message = "Observações não podem exceder 500 caracteres")
        String notes
) {
}
