package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.models.SvoTeam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SvoTeamResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        List<SvoTeamMemberResponse> members,
        Instant createdAt,
        Instant updatedAt
) {
    public static SvoTeamResponse from(SvoTeam team) {
        return new SvoTeamResponse(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.isActive(),
                team.getMembers() == null
                        ? List.of()
                        : team.getMembers().stream().map(SvoTeamMemberResponse::from).toList(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}
