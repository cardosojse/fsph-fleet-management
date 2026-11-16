package br.com.gestaofrota.infra.startup;

import br.com.gestaofrota.domain.enums.UserRole;
import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.config.startup.StagingDemoInitializer;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.infra.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StagingDemoInitializerTests {

    private static final String EMAIL = "demo@frota.test";
    private static final String NAME = "Demonstracao de Homologacao";
    private final String password = UUID.randomUUID().toString();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository userRepository;
    private SectorRepository sectorRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        sectorRepository = mock(SectorRepository.class);
    }

    @Test
    void createsDemoWithEncodedPasswordAndExistingSector() {
        Sector sector = new Sector(NAME);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(sectorRepository.findByNameIgnoreCase(NAME)).thenReturn(Optional.of(sector));

        initializer(password).run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getName()).isEqualTo(NAME);
        assertThat(created.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(created.getSector()).isSameAs(sector);
        assertThat(created.isActive()).isTrue();
        assertThat(encoder.matches(password, created.getPassword())).isTrue();
        assertThat(created.getPassword()).doesNotContain(password);
        verify(sectorRepository, never()).save(any());
    }

    @Test
    void createsDedicatedSectorWhenMissing() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(sectorRepository.findByNameIgnoreCase(NAME)).thenReturn(Optional.empty());
        when(sectorRepository.save(any(Sector.class))).thenAnswer(call -> call.getArgument(0));

        initializer(password).run(null);

        ArgumentCaptor<Sector> sectorCaptor = ArgumentCaptor.forClass(Sector.class);
        verify(sectorRepository).save(sectorCaptor.capture());
        assertThat(sectorCaptor.getValue().getName()).isEqualTo(NAME);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rotatesPasswordWithoutReplacingUserOrChangingItsSector() {
        Sector sector = new Sector(NAME);
        User existing = new User(sector, NAME, EMAIL, encoder.encode(UUID.randomUUID().toString()), UserRole.ADMIN);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        initializer(password).run(null);

        verify(userRepository).save(same(existing));
        assertThat(existing.getSector()).isSameAs(sector);
        assertThat(encoder.matches(password, existing.getPassword())).isTrue();
        verifyNoInteractions(sectorRepository);
    }

    @Test
    void doesNotRehashOrWriteAnUnchangedDemo() {
        String hash = encoder.encode(password);
        User existing = new User(new Sector(NAME), NAME, EMAIL, hash, UserRole.ADMIN);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        initializer(password).run(null);

        assertThat(existing.getPassword()).isEqualTo(hash);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(sectorRepository);
    }

    @Test
    void reactivatesOnlyTheManagedDemo() {
        User existing = new User(new Sector(NAME), NAME, EMAIL, encoder.encode(password), UserRole.ADMIN);
        existing.deactivate();
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        initializer(password).run(null);

        assertThat(existing.isActive()).isTrue();
        verify(userRepository).save(same(existing));
    }

    @Test
    void refusesToTakeOverAnUnmanagedAccount() {
        String hash = encoder.encode(password);
        User existing = new User(new Sector(NAME), "Outro administrador", EMAIL, hash, UserRole.ADMIN);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> initializer(password).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unmanaged account");

        assertThat(existing.getPassword()).isEqualTo(hash);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(sectorRepository);
    }

    @Test
    void refusesToElevateAnExistingRole() {
        UserRole restrictedRole = java.util.Arrays.stream(UserRole.values())
                .filter(role -> role != UserRole.ADMIN).findFirst().orElseThrow();
        User existing = new User(new Sector(NAME), NAME, EMAIL, encoder.encode(password), restrictedRole);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> initializer(password).run(null)).isInstanceOf(IllegalStateException.class);

        assertThat(existing.getRole()).isEqualTo(restrictedRole);
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "                                                                        ",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsUnsafePasswordBeforeAccessingDatabase(String invalidPassword) {
        assertThatThrownBy(() -> initializer(invalidPassword).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("STAGING_DEMO_PASSWORD");
        verifyNoInteractions(userRepository, sectorRepository);
    }

    @Test
    void refusesProductionEnvironmentEvenWhenStagingProfileWasAccidentallyEnabled() {
        StagingDemoInitializer initializer = new StagingDemoInitializer(
                userRepository, sectorRepository, encoder, "production", password);

        assertThatThrownBy(() -> initializer.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENVIRONMENT=staging");
        verifyNoInteractions(userRepository, sectorRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod", "production", "staging,prod", "staging,production"})
    void neverRegistersForProductionOrDefaultProfiles(String profiles) {
        context().withPropertyValues("spring.profiles.active=" + profiles, "app.staging.demo.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(StagingDemoInitializer.class));
    }

    @Test
    void requiresExplicitOptInEvenInStaging() {
        context().withPropertyValues("spring.profiles.active=staging")
                .run(context -> assertThat(context).doesNotHaveBean(StagingDemoInitializer.class));
    }

    @Test
    void registersOnlyInOptedInStaging() {
        context().withPropertyValues("spring.profiles.active=staging", "app.staging.demo.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(StagingDemoInitializer.class));
    }

    private StagingDemoInitializer initializer(String demoPassword) {
        return new StagingDemoInitializer(userRepository, sectorRepository, encoder, "staging", demoPassword);
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(StagingDemoInitializer.class)
                .withBean(UserRepository.class, () -> userRepository)
                .withBean(SectorRepository.class, () -> sectorRepository)
                .withBean(PasswordEncoder.class, () -> encoder);
    }
}
