package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.AddTeamMemberRequest;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.InvalidRequestException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.SvoTeam;
import br.com.gestaofrota.domain.models.SvoTeamMember;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SvoTeamRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SvoTeamService {

    private final SvoTeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public SvoTeamService(
            SvoTeamRepository teamRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SvoTeam create(String name, String description, List<AddTeamMemberRequest> initialMembers, AuthenticatedUser actor) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Nome da equipe é obrigatório");
        }
        String normalizedName = name.trim();
        if (teamRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ConflictException("Já existe uma equipe SVO com este nome");
        }

        SvoTeam team = new SvoTeam(normalizedName, description);
        if (initialMembers != null) {
            for (AddTeamMemberRequest memberReq : initialMembers) {
                User user = userRepository.findById(memberReq.userId())
                        .orElseThrow(() -> new ResourceNotFoundException("Usuário", memberReq.userId()));
                team.addMember(user, memberReq.roleInTeam());
            }
        }

        SvoTeam saved = teamRepository.save(team);
        auditLogService.record(actor, "SVO_TEAM_CREATED", "SVO_TEAM", saved.getId().toString(),
                "Equipe SVO criada: " + saved.getName() + " com " + (initialMembers != null ? initialMembers.size() : 0) + " membros");
        return saved;
    }

    @Transactional
    public SvoTeam update(UUID id, String name, String description, AuthenticatedUser actor) {
        SvoTeam team = find(id);
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Nome da equipe é obrigatório");
        }
        String normalizedName = name.trim();
        if (teamRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new ConflictException("Já existe outra equipe SVO com este nome");
        }

        team.rename(normalizedName);
        team.updateDescription(description);

        auditLogService.record(actor, "SVO_TEAM_UPDATED", "SVO_TEAM", team.getId().toString(),
                "Equipe SVO atualizada: " + team.getName());
        return team;
    }

    @Transactional
    public void deactivate(UUID id, AuthenticatedUser actor) {
        SvoTeam team = find(id);
        team.deactivate();
        auditLogService.record(actor, "SVO_TEAM_DEACTIVATED", "SVO_TEAM", team.getId().toString(),
                "Equipe SVO inativada: " + team.getName());
    }

    @Transactional
    public void reactivate(UUID id, AuthenticatedUser actor) {
        SvoTeam team = find(id);
        team.reactivate();
        auditLogService.record(actor, "SVO_TEAM_REACTIVATED", "SVO_TEAM", team.getId().toString(),
                "Equipe SVO reativada: " + team.getName());
    }

    @Transactional
    public SvoTeamMember addMember(UUID teamId, UUID userId, String roleInTeam, AuthenticatedUser actor) {
        SvoTeam team = find(teamId);
        if (!team.isActive()) {
            throw new ConflictException("Não é possível adicionar membros a uma equipe inativa");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", userId));

        SvoTeamMember member = team.addMember(user, roleInTeam);
        auditLogService.record(actor, "SVO_TEAM_MEMBER_ADDED", "SVO_TEAM", team.getId().toString(),
                "Membro " + user.getName() + " adicionado à equipe " + team.getName() + " como " + roleInTeam);
        return member;
    }

    @Transactional
    public void removeMember(UUID teamId, UUID memberId, AuthenticatedUser actor) {
        SvoTeam team = find(teamId);
        team.removeMember(memberId);
        auditLogService.record(actor, "SVO_TEAM_MEMBER_REMOVED", "SVO_TEAM", team.getId().toString(),
                "Membro " + memberId + " removido da equipe " + team.getName());
    }

    @Transactional(readOnly = true)
    public List<SvoTeam> list(Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            return teamRepository.findByActiveTrueOrderByNameAsc();
        }
        return teamRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public SvoTeam find(UUID id) {
        return teamRepository.findWithMembersById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipe SVO", id));
    }
}
