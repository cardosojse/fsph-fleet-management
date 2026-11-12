package br.com.gestaofrota.api.v1.dtos.responses;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.SvoTeamMember;

import java.time.Instant;
import java.util.UUID;

public record SvoTeamMemberResponse(
        UUID id,
        UUID userId,
        String userName,
        String userEmail,
        UserRole userRole,
        String roleInTeam,
        boolean active,
        Instant createdAt
) {
    public static SvoTeamMemberResponse from(SvoTeamMember member) {
        return new SvoTeamMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getUser().getRole(),
                member.getRoleInTeam(),
                member.isActive(),
                member.getCreatedAt()
        );
    }
}
