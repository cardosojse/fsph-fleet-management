package br.com.gestaofrota;

import br.com.gestaofrota.api.v1.dtos.requests.AddTeamMemberRequest;
import br.com.gestaofrota.api.v1.dtos.responses.SvoDutyShiftResponse;
import br.com.gestaofrota.api.v1.dtos.responses.SvoTeamResponse;
import br.com.gestaofrota.application.services.SvoDutyShiftService;
import br.com.gestaofrota.application.services.SvoTeamService;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.domain.enums.SvoShiftStatus;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.SvoTeam;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.infra.security.models.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Deliberately no test transaction: controllers map DTOs after the service returns.
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class SvoDutyShiftSerializationIntegrationTests extends PostgresIntegrationTest {
    @Autowired private SvoDutyShiftService shifts;
    @Autowired private SvoTeamService teams;
    @Autowired private SectorRepository sectors;
    @Autowired private UserRepository users;

    @Test
    void serializesCreatedShiftWithMembersAfterServiceTransactionCloses() {
        var actor = actor();
        var team = teamWithMember(actor);
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);

        var shift = shifts.create(team.getId(), null, start, start.plus(6, ChronoUnit.HOURS),
                "DIURNO", null, actor);

        var response = SvoDutyShiftResponse.from(shift);
        assertThat(response.teamId()).isEqualTo(team.getId());
        assertThat(response.teamMembers()).singleElement().satisfies(member -> {
            assertThat(member.userName()).isEqualTo("Integrante SVO");
            assertThat(member.userRole()).isEqualTo(UserRole.EQUIPE_COLETA_SVO);
        });
        assertThat(SvoDutyShiftResponse.from(shifts.find(shift.getId())).teamMembers()).hasSize(1);
    }

    @Test
    void serializesUpdatedShiftWhenChangingTeamAndCanceling() {
        var actor = actor();
        var original = teams.create("Original " + UUID.randomUUID(), null, List.of(), actor);
        var replacement = teamWithMember(actor);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        var shift = shifts.create(original.getId(), null, start, start.plus(6, ChronoUnit.HOURS),
                null, null, actor);

        var updated = shifts.update(shift.getId(), replacement.getId(), null, start,
                start.plus(6, ChronoUnit.HOURS), null, null, actor);

        var response = SvoDutyShiftResponse.from(updated);
        assertThat(response.teamId()).isEqualTo(replacement.getId());
        assertThat(response.teamMembers()).hasSize(1);
        assertThat(SvoDutyShiftResponse.from(shifts.cancel(shift.getId(), actor)).status())
                .isEqualTo(SvoShiftStatus.CANCELED);
    }

    private AuthenticatedUser actor() {
        var sector = sectors.save(new Sector("SVO serialization " + UUID.randomUUID()));
        var user = users.save(new User(sector, "Gestor SVO", UUID.randomUUID() + "@example.test",
                "not-used-for-login", UserRole.GESTOR_SVO));
        return new AuthenticatedUser(user.getId(), sector.getId(), user.getName(), user.getEmail(),
                user.getPassword(), user.getRole(), true, Set.of(Permission.SVO_SHIFTS_WRITE, Permission.SVO_TEAMS_WRITE));
    }

    @Test
    void serializesListedTeamsWithMembersAfterServiceTransactionCloses() {
        var team = teamWithMember(actor());

        for (Boolean activeOnly : new Boolean[]{null, true}) {
            var response = teams.list(activeOnly).stream()
                    .filter(item -> item.getId().equals(team.getId()))
                    .map(SvoTeamResponse::from).findFirst().orElseThrow();
            assertThat(response.members()).singleElement()
                    .satisfies(member -> assertThat(member.userName()).isEqualTo("Integrante SVO"));
        }
    }

    private SvoTeam teamWithMember(AuthenticatedUser actor) {
        var sector = sectors.findById(actor.sectorId()).orElseThrow();
        var member = users.save(new User(sector, "Integrante SVO", UUID.randomUUID() + "@example.test",
                "not-used-for-login", UserRole.EQUIPE_COLETA_SVO));
        return teams.create("Equipe " + UUID.randomUUID(), null,
                List.of(new AddTeamMemberRequest(member.getId(), "Coleta")), actor);
    }
}
