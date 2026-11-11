package br.com.gestaofrota.infra.security.services;

import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.enums.Permission;
import br.com.gestaofrota.application.services.RolePermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RolePermissionService rolePermissionService;

    @Test
    void loadsActiveUserWithRoleAsAuthority() {
        User administrator = new User(
                new Sector("Administrativo"),
                "Administrador",
                "admin@exemplo.com",
                "senha-codificada",
                UserRole.ADMIN
        );
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue("admin@exemplo.com"))
                .thenReturn(Optional.of(administrator));
        when(rolePermissionService.find(UserRole.ADMIN)).thenReturn(Set.of(Permission.USERS_READ));

        UserDetails result = new CustomUserDetailsService(userRepository, rolePermissionService, new MockEnvironment())
                .loadUserByUsername(" ADMIN@EXEMPLO.COM ");

        assertThat(result.getUsername()).isEqualTo("admin@exemplo.com");
        assertThat(result.getPassword()).isEqualTo("senha-codificada");
        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "USERS_READ");
        verify(userRepository).findByEmailIgnoreCaseAndActiveTrue("admin@exemplo.com");
    }

    @Test
    void rejectsMissingOrInactiveUserWithoutRevealingWhichCaseOccurred() {
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue("inativo@exemplo.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CustomUserDetailsService(userRepository, rolePermissionService, new MockEnvironment())
                .loadUserByUsername("inativo@exemplo.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Credenciais inválidas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod", "production", "staging,prod", "staging,production"})
    void rejectsDemoLoginAndJwtReloadOutsideStaging(String profiles) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.environment", "staging")
                .withProperty("app.staging.demo.enabled", "true");
        environment.setActiveProfiles(profiles.split(","));

        assertThatThrownBy(() -> new CustomUserDetailsService(userRepository, rolePermissionService, environment)
                .loadUserByUsername(" DEMO@FROTA.TEST "))
                .isInstanceOf(UsernameNotFoundException.class).hasMessage("Credenciais inválidas");
        verifyNoInteractions(userRepository, rolePermissionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true"})
    void requiresBothStagingEnvironmentAndExplicitOptIn(String enabled) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.environment", "true".equals(enabled) ? "production" : "staging")
                .withProperty("app.staging.demo.enabled", enabled);
        environment.setActiveProfiles("staging");

        assertThatThrownBy(() -> new CustomUserDetailsService(userRepository, rolePermissionService, environment)
                .loadUserByUsername("demo@frota.test"))
                .isInstanceOf(UsernameNotFoundException.class);
        verifyNoInteractions(userRepository, rolePermissionService);
    }

    @Test
    void loadsDemoThroughTheUsualRoleAndPermissionChecksInStaging() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.environment", "staging")
                .withProperty("app.staging.demo.enabled", "true");
        environment.setActiveProfiles("staging");
        User demo = new User(new Sector("Demonstracao de Homologacao"), "Demonstracao de Homologacao",
                "demo@frota.test", "hash", UserRole.ADMIN);
        when(userRepository.findByEmailIgnoreCaseAndActiveTrue("demo@frota.test")).thenReturn(Optional.of(demo));
        when(rolePermissionService.find(UserRole.ADMIN)).thenReturn(Set.of(Permission.USERS_READ));

        UserDetails result = new CustomUserDetailsService(userRepository, rolePermissionService, environment)
                .loadUserByUsername("demo@frota.test");

        assertThat(result.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "USERS_READ");
    }
}
