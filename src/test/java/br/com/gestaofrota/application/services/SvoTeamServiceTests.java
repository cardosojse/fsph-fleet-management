package br.com.gestaofrota.application.services;

import br.com.gestaofrota.api.v1.dtos.requests.AddTeamMemberRequest;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.domain.exceptions.ResourceNotFoundException;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.SvoTeam;
import br.com.gestaofrota.domain.models.SvoTeamMember;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SvoTeamRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SvoTeamServiceTests {

    @Mock
    private SvoTeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    private SvoTeamService teamService;

    private AuthenticatedUser actor;
    private Sector sector;

    @BeforeEach
    void setUp() {
        teamService = new SvoTeamService(teamRepository, userRepository, auditLogService);
        actor = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Gestor Frota",
                "gestor@fsph.se.gov.br",
                "secret",
                UserRole.GESTOR_FROTA,
                true,
                Set.of(Permission.SVO_TEAMS_READ, Permission.SVO_TEAMS_WRITE)
        );
        sector = new Sector("SVO Central");
        ReflectionTestUtils.setField(sector, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("Should create SVO team with initial members successfully")
    void createTeamSuccessfully() {
        User user = new User(sector, "Carlos Coletor", "carlos@fsph.se.gov.br", "hash", UserRole.EQUIPE_COLETA_SVO);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        when(teamRepository.existsByNameIgnoreCase("Equipe Alfa")).thenReturn(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(teamRepository.save(any(SvoTeam.class))).thenAnswer(invocation -> {
            SvoTeam team = invocation.getArgument(0);
            ReflectionTestUtils.setField(team, "id", UUID.randomUUID());
            return team;
        });

        AddTeamMemberRequest initialMember = new AddTeamMemberRequest(user.getId(), "COLETOR");
        SvoTeam result = teamService.create("Equipe Alfa", "Plantão Noturno", List.of(initialMember), actor);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Equipe Alfa");
        assertThat(result.getDescription()).isEqualTo("Plantão Noturno");
        assertThat(result.getMembers()).hasSize(1);
        assertThat(result.getMembers().iterator().next().getUser().getName()).isEqualTo("Carlos Coletor");

        verify(auditLogService).record(eq(actor), eq("SVO_TEAM_CREATED"), eq("SVO_TEAM"), any(), any());
    }

    @Test
    @DisplayName("Should throw ConflictException when team name already exists")
    void throwWhenTeamNameExists() {
        when(teamRepository.existsByNameIgnoreCase("Equipe Alfa")).thenReturn(true);

        assertThatThrownBy(() -> teamService.create("Equipe Alfa", "Desc", null, actor))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Já existe uma equipe SVO com este nome");
    }

    @Test
    @DisplayName("Should add member to existing team successfully")
    void addMemberSuccessfully() {
        SvoTeam team = new SvoTeam("Equipe Bravo", "Desc");
        UUID teamId = UUID.randomUUID();
        ReflectionTestUtils.setField(team, "id", teamId);

        User user = new User(sector, "Ana Atendente", "ana@fsph.se.gov.br", "hash", UserRole.ATENDENTE_SVO);
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);

        when(teamRepository.findWithMembersById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        SvoTeamMember member = teamService.addMember(teamId, userId, "ATENDENTE", actor);

        assertThat(member).isNotNull();
        assertThat(member.getUser().getId()).isEqualTo(userId);
        assertThat(member.getRoleInTeam()).isEqualTo("ATENDENTE");
        assertThat(team.getMembers()).hasSize(1);

        verify(auditLogService).record(eq(actor), eq("SVO_TEAM_MEMBER_ADDED"), eq("SVO_TEAM"), eq(teamId.toString()), any());
    }

    @Test
    @DisplayName("Should throw ConflictException when user is already active member of the team")
    void throwWhenUserAlreadyMember() {
        SvoTeam team = new SvoTeam("Equipe Charlie", "Desc");
        UUID teamId = UUID.randomUUID();
        ReflectionTestUtils.setField(team, "id", teamId);

        User user = new User(sector, "Marcos Silva", "marcos@fsph.se.gov.br", "hash", UserRole.EQUIPE_COLETA_SVO);
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);

        team.addMember(user, "COLETOR");

        when(teamRepository.findWithMembersById(teamId)).thenReturn(Optional.of(team));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> teamService.addMember(teamId, userId, "MOTORISTA", actor))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("já é membro ativo desta equipe");
    }

    @Test
    @DisplayName("Should remove member from team successfully")
    void removeMemberSuccessfully() {
        SvoTeam team = new SvoTeam("Equipe Delta", "Desc");
        UUID teamId = UUID.randomUUID();
        ReflectionTestUtils.setField(team, "id", teamId);

        User user = new User(sector, "Lucas Oliveira", "lucas@fsph.se.gov.br", "hash", UserRole.EQUIPE_COLETA_SVO);
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);

        SvoTeamMember member = team.addMember(user, "COLETOR");
        UUID memberId = UUID.randomUUID();
        ReflectionTestUtils.setField(member, "id", memberId);

        when(teamRepository.findWithMembersById(teamId)).thenReturn(Optional.of(team));

        teamService.removeMember(teamId, memberId, actor);

        assertThat(team.getMembers()).isEmpty();
        verify(auditLogService).record(eq(actor), eq("SVO_TEAM_MEMBER_REMOVED"), eq("SVO_TEAM"), eq(teamId.toString()), any());
    }

    @Test
    @DisplayName("Should deactivate and reactivate team")
    void deactivateAndReactivateTeam() {
        SvoTeam team = new SvoTeam("Equipe Echo", "Desc");
        UUID teamId = UUID.randomUUID();
        ReflectionTestUtils.setField(team, "id", teamId);

        when(teamRepository.findWithMembersById(teamId)).thenReturn(Optional.of(team));

        teamService.deactivate(teamId, actor);
        assertThat(team.isActive()).isFalse();
        verify(auditLogService).record(eq(actor), eq("SVO_TEAM_DEACTIVATED"), eq("SVO_TEAM"), eq(teamId.toString()), any());

        teamService.reactivate(teamId, actor);
        assertThat(team.isActive()).isTrue();
        verify(auditLogService).record(eq(actor), eq("SVO_TEAM_REACTIVATED"), eq("SVO_TEAM"), eq(teamId.toString()), any());
    }
}
