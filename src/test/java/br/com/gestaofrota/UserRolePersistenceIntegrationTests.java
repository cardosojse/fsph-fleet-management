package br.com.gestaofrota;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.application.services.RolePermissionService;
import br.com.gestaofrota.application.services.UserService;
import br.com.gestaofrota.domain.exceptions.ConflictException;
import br.com.gestaofrota.infra.config.startup.AdminProperties;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserRolePersistenceIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private UserService userService;

    @Autowired
    private AdminProperties adminProperties;

    @Test
    void keepsTheLastAdministratorActiveWithJpaLock() {
        User administrator = userRepository.findByEmailIgnoreCase(adminProperties.email()).orElseThrow();

        assertThatThrownBy(() -> userService.deactivate(administrator.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(administrator.isActive()).isTrue();
    }

    @Test
    void seedsTheDefaultAccessMatrixForEveryRole() {
        assertThat(rolePermissionService.find(UserRole.ADMIN))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
        for (UserRole role : UserRole.values()) {
            if (role != UserRole.ADMIN) {
                Set<Permission> expected = switch (role) {
                    case GESTOR_FROTA -> Set.of(Permission.SECTORS_READ, Permission.VEHICLES_READ,
                            Permission.VEHICLES_WRITE, Permission.DRIVERS_READ, Permission.DRIVERS_WRITE);
                    case MOTORISTA -> Set.of(Permission.SECTORS_READ, Permission.VEHICLES_ASSIGNED_READ,
                            Permission.TRIPS_WRITE);
                    default -> Set.of(Permission.SECTORS_READ);
                };
                assertThat(rolePermissionService.find(role)).isEqualTo(expected);
            }
        }
    }

    @Test
    void roundTripsEveryRoleThroughJsonJpaAndPostgres() throws Exception {
        Sector sector = sectorRepository.saveAndFlush(new Sector("Teste de perfis " + UUID.randomUUID()));

        for (UserRole role : UserRole.values()) {
            String json = objectMapper.writeValueAsString(role);
            assertThat(json).isEqualTo("\"" + role.name() + "\"");
            assertThat(objectMapper.readValue(json, UserRole.class)).isEqualTo(role);

            User saved = userRepository.saveAndFlush(new User(
                    sector,
                    "Usuário " + role.name(),
                    role.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@example.com",
                    "password-hash",
                    role
            ));
            UUID userId = saved.getId();

            entityManager.clear();

            assertThat(userRepository.findById(userId)).get().extracting(User::getRole).isEqualTo(role);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT role FROM users WHERE id = ?",
                    String.class,
                    userId
            )).isEqualTo(role.name());
        }
    }

    @Test
    void schemaRoleLengthMatchesJpaMappingAndFitsEveryRole() throws NoSuchFieldException {
        int jpaLength = User.class.getDeclaredField("role").getAnnotation(Column.class).length();
        Integer databaseLength = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'users'
                  AND column_name = 'role'
                """, Integer.class);
        int longestRole = Arrays.stream(UserRole.values())
                .map(Enum::name)
                .mapToInt(String::length)
                .max()
                .orElseThrow();

        assertThat(databaseLength).isEqualTo(jpaLength);
        assertThat(databaseLength).isGreaterThanOrEqualTo(longestRole);
    }

    @Test
    void databaseConstraintRejectsLegacyInstitutionalUserRole() {
        Sector sector = sectorRepository.saveAndFlush(new Sector("Teste de perfil legado " + UUID.randomUUID()));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (sector_id, name, email, password_hash, role)
                VALUES (?, ?, ?, ?, ?)
                """,
                sector.getId(),
                "Usuário legado",
                "legacy-" + UUID.randomUUID() + "@example.com",
                "password-hash",
                "USUARIO_INSTITUCIONAL"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sectorNameIsUniqueIgnoringCaseAndSurroundingSpaces() {
        String name = "Setor " + UUID.randomUUID();
        sectorRepository.saveAndFlush(new Sector(name));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO sectors (name) VALUES (?)", " " + name.toUpperCase(Locale.ROOT) + " "
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userEmailIsUniqueIgnoringCaseAndSurroundingSpaces() {
        Sector sector = sectorRepository.saveAndFlush(new Sector("Setor " + UUID.randomUUID()));
        String email = "user-" + UUID.randomUUID() + "@example.com";
        userRepository.saveAndFlush(new User(sector, "Primeiro", email, "hash", UserRole.ADMIN));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO users (sector_id, name, email, password_hash, role)
                VALUES (?, ?, ?, ?, ?)
                """, sector.getId(), "Segundo", " " + email.toUpperCase(Locale.ROOT) + " ", "hash", "ADMIN"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
