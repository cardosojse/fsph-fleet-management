package br.com.gestaofrota.infra.startup;

import br.com.gestaofrota.domain.models.Sector;
import br.com.gestaofrota.infra.config.startup.AdminInitializer;
import br.com.gestaofrota.infra.config.startup.AdminProperties;
import br.com.gestaofrota.infra.repositories.SectorRepository;
import br.com.gestaofrota.domain.models.User;
import br.com.gestaofrota.infra.repositories.UserRepository;
import br.com.gestaofrota.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SectorRepository sectorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdministratorWithNormalizedEmailAndEncodedPassword() throws Exception {
        AdminProperties properties = new AdminProperties(
                " Administrador ", " ADMIN@EXEMPLO.COM ", "senha", " Administrativo "
        );
        Sector sector = new Sector("Administrativo");
        when(userRepository.existsByEmailIgnoreCase("admin@exemplo.com")).thenReturn(false);
        when(sectorRepository.findByNameIgnoreCase("Administrativo")).thenReturn(Optional.of(sector));
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(4);

        new AdminInitializer(userRepository, sectorRepository, bcrypt, properties).run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getName()).isEqualTo("Administrador");
        assertThat(savedUser.getEmail()).isEqualTo("admin@exemplo.com");
        assertThat(savedUser.getPassword()).isNotEqualTo("senha");
        assertThat(bcrypt.matches("senha", savedUser.getPassword())).isTrue();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedUser.getSector()).isSameAs(sector);
        verify(sectorRepository, never()).save(any());
    }

    @Test
    void createsAdministrativeSectorWhenItDoesNotExist() throws Exception {
        AdminProperties properties = new AdminProperties(
                "Administrador", "admin@exemplo.com", "senha", "Administrativo"
        );
        when(userRepository.existsByEmailIgnoreCase("admin@exemplo.com")).thenReturn(false);
        when(sectorRepository.findByNameIgnoreCase("Administrativo")).thenReturn(Optional.empty());
        when(sectorRepository.save(any(Sector.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("senha")).thenReturn("senha-codificada");

        new AdminInitializer(userRepository, sectorRepository, passwordEncoder, properties).run(null);

        ArgumentCaptor<Sector> sectorCaptor = ArgumentCaptor.forClass(Sector.class);
        verify(sectorRepository).save(sectorCaptor.capture());
        assertThat(sectorCaptor.getValue().getName()).isEqualTo("Administrativo");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void doesNothingWhenAdministratorAlreadyExists() throws Exception {
        AdminProperties properties = new AdminProperties(
                "Administrador", "ADMIN@EXEMPLO.COM", "senha", "Administrativo"
        );
        when(userRepository.existsByEmailIgnoreCase("admin@exemplo.com")).thenReturn(true);

        new AdminInitializer(userRepository, sectorRepository, passwordEncoder, properties).run(null);

        verifyNoInteractions(sectorRepository);
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void reusesAdministrativeAliasWithoutRenamingOrReassigningExistingAccounts() {
        AdminProperties properties = new AdminProperties(
                "Administrador", "admin@exemplo.com", "senha", "Administrativo"
        );
        Sector existing = new Sector("Administração");
        when(sectorRepository.findByNameIgnoreCase("Administrativo")).thenReturn(Optional.empty());
        when(sectorRepository.findAll()).thenReturn(List.of(new Sector("Lacen"), existing));
        when(passwordEncoder.encode("senha")).thenReturn("hash");

        new AdminInitializer(userRepository, sectorRepository, passwordEncoder, properties).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getSector()).isSameAs(existing);
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(existing.getName()).isEqualTo("Administração");
        verify(sectorRepository, never()).save(any());
    }
}
